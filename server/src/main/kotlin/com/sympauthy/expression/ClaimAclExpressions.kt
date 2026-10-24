package com.sympauthy.expression

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.parser.ASTNode
import com.ezylang.evalex.parser.ParseException
import com.ezylang.evalex.parser.Token.TokenType
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.claim.ClaimAclContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.Map.entry

/**
 * What an ACL expression names, read out of it when the configuration is.
 *
 * Every argument here was written as a literal, so what a door opens on is known before any request: the
 * scopes it is listed under, and the conditions a refused write is challenged for.
 */
data class ClaimAclExpressionTerms(
    /**
     * The scope of every `SCOPE_IS_CONSENTED` the expression calls.
     */
    val consentedScopes: Set<String>,
    /**
     * The scope of every `SCOPE_IS_GRANTED` the expression calls.
     */
    val grantedScopes: Set<String>,
    /**
     * The duration of every `AUTHENTICATION_IS_YOUNGER_THAN` the expression calls.
     */
    val authenticationAges: Set<Duration>,
    /**
     * Whether the expression calls `TOKEN_IS_BOUND`.
     */
    val asksForBoundToken: Boolean
)

/**
 * The functions a claim's ACL expression may call, and the two things done with them: refusing an
 * expression when the configuration is read, and evaluating one at the door it is written on.
 *
 * The doors have two function sets rather than one. The flow is the door with no token behind it — the
 * person is on this server's own pages having just authenticated — so it offers the consent and the client
 * and nothing about a token: a function of the token written there would answer false for every person
 * forever, and is refused at startup as an unknown function instead. The four doors a token comes through
 * offer every function.
 *
 * The two uses have separate entry points rather than one taking nullable values, for
 * [the reason given beside the scope granting rules][ScopeGrantingRuleExpressions].
 */
object ClaimAclExpressions {

    /**
     * Configuration for the flow's door, opened to the [client] that started the flow for a person who
     * consented to [consentedScopes].
     */
    fun flowConfiguration(client: Client, consentedScopes: List<String>): ExpressionConfiguration {
        return defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(client)),
            entry(ScopeIsConsentedFunction.FUNCTION_NAME, ScopeIsConsentedFunction(consentedScopes))
        )
    }

    /**
     * Configuration for a door a token comes through, evaluated in [context] at [now].
     */
    fun tokenConfiguration(context: ClaimAclContext, now: LocalDateTime): ExpressionConfiguration {
        val authenticationAge = context.authenticationDate?.let { Duration.between(it, now) }
        return defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(context.client)),
            entry(ScopeIsConsentedFunction.FUNCTION_NAME, ScopeIsConsentedFunction(context.consentedScopes)),
            entry(ScopeIsGrantedFunction.FUNCTION_NAME, ScopeIsGrantedFunction(context.grantedScopes)),
            entry(
                AuthenticationIsYoungerThanFunction.FUNCTION_NAME,
                AuthenticationIsYoungerThanFunction(authenticationAge)
            ),
            entry(TokenIsBoundFunction.FUNCTION_NAME, TokenIsBoundFunction(context.bound))
        )
    }

    /**
     * Refuse [expressionString] unless it is usable on the flow's door, and return what it names.
     *
     * One that is not throws an [InvalidRuleExpressionException] naming which of the two ways it failed; a
     * function of the token is an unknown function here, and a function taking a literal given anything else
     * does not parse.
     */
    fun validateFlowExpression(expressionString: String): ClaimAclExpressionTerms {
        val configuration = defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction()),
            entry(ScopeIsConsentedFunction.FUNCTION_NAME, ScopeIsConsentedFunction())
        )
        return validate(expressionString, configuration)
    }

    /**
     * Refuse [expressionString] unless it is usable on a door a token comes through, and return what it
     * names, the same way and with the same failures as on the flow's.
     */
    fun validateTokenExpression(expressionString: String): ClaimAclExpressionTerms {
        val configuration = defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction()),
            entry(ScopeIsConsentedFunction.FUNCTION_NAME, ScopeIsConsentedFunction()),
            entry(ScopeIsGrantedFunction.FUNCTION_NAME, ScopeIsGrantedFunction()),
            entry(AuthenticationIsYoungerThanFunction.FUNCTION_NAME, AuthenticationIsYoungerThanFunction()),
            entry(TokenIsBoundFunction.FUNCTION_NAME, TokenIsBoundFunction())
        )
        return validate(expressionString, configuration)
    }

    private fun validate(expressionString: String, configuration: ExpressionConfiguration): ClaimAclExpressionTerms {
        evaluateRuleExpression(expressionString, configuration)
        // Read off the tree rather than off an evaluation: `&&` and `||` stop at the first operand that
        // decides them, so a call an evaluation never reaches would go unread.
        val calls = try {
            Expression(expressionString, configuration).allASTNodes
                .filter { it.token.type == TokenType.FUNCTION }
        } catch (e: ParseException) {
            throw invalidExpression(expressionString, e.message)
        }
        fun literalsOf(functionName: String): Set<String> = calls
            .filter { it.token.value.equals(functionName, ignoreCase = true) }
            .mapTo(mutableSetOf()) { literalArgumentOf(expressionString, it) }
        return ClaimAclExpressionTerms(
            consentedScopes = literalsOf(ScopeIsConsentedFunction.FUNCTION_NAME),
            grantedScopes = literalsOf(ScopeIsGrantedFunction.FUNCTION_NAME),
            // Read here as well as by the function, so a duration in an operand no evaluation reaches is
            // refused too.
            authenticationAges = literalsOf(AuthenticationIsYoungerThanFunction.FUNCTION_NAME).mapTo(mutableSetOf()) {
                AuthenticationIsYoungerThanFunction.readDurationOrNull(it)
                    ?: throw invalidExpression(expressionString, "$it is not a duration")
            },
            asksForBoundToken = calls.any {
                it.token.value.equals(TokenIsBoundFunction.FUNCTION_NAME, ignoreCase = true)
            }
        )
    }

    /**
     * The literal [call] was given. A scope is checked against the scopes this deployment declares and a
     * duration read as every duration key is, both when the configuration is read, so neither may be an
     * expression known only once it is evaluated.
     */
    private fun literalArgumentOf(expressionString: String, call: ASTNode): String {
        val argument = call.parameters.singleOrNull()?.token
        if (argument?.type != TokenType.STRING_LITERAL) {
            throw invalidExpression(expressionString, "${call.token.value} takes a string literal")
        }
        return argument.value
    }

    private fun invalidExpression(expressionString: String, parseError: String?) = InvalidRuleExpressionException(
        expressionString = expressionString,
        configMessageId = "config.expression.invalid",
        businessErrorDetailsId = "expression.evaluate.failed",
        parseError = parseError
    )
}
