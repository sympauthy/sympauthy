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
     * The scope of every `SCOPE_IS_CONSENTED` the expression calls, wherever it calls it.
     */
    val consentedScopes: Set<String>,
    /**
     * The scope of every `SCOPE_IS_GRANTED` the expression calls, wherever it calls it.
     */
    val grantedScopes: Set<String>,
    /**
     * The duration of every `AUTHENTICATION_IS_YOUNGER_THAN` the expression calls, wherever it calls it.
     */
    val authenticationAges: Set<Duration>,
    /**
     * The conditions meeting which can open the door.
     */
    val opening: ClaimAclOpeningTerms
)

/**
 * The conditions of an ACL expression that a different token could meet to open the door: the calls nothing
 * but `&&` and `||` stand above.
 *
 * A call under anything else is left out, whatever it is under. Under `!` meeting the condition shuts the
 * door, and under a comparison or a function of its own what meeting it does is the expression's to say; a
 * condition asked for there is neither one a scope unlocks nor one a refused client can be told to meet.
 */
data class ClaimAclOpeningTerms(
    val consentedScopes: Set<String>,
    val authenticationAges: Set<Duration>,
    val asksForBoundToken: Boolean
)

/**
 * The functions a claim's ACL expression may call, and the two things done with them: refusing an
 * expression when the configuration is read, and evaluating one at the door it is written on.
 *
 * The doors have two function sets rather than one, each declared once and read by both of its entry points.
 * The flow is the door with no token behind it — the person is on this server's own pages having just
 * authenticated — so it offers the consent and the client and nothing about a token: a function of the token
 * written there would answer false for every person forever, and is refused at startup as an unknown
 * function instead. The four doors a token comes through offer every function.
 *
 * The two uses have separate entry points rather than one taking nullable values, for
 * [the reason given beside the scope granting rules][ScopeGrantingRuleExpressions].
 */
object ClaimAclExpressions {

    /**
     * Configuration for the flow's door, opened to the [client] that started the flow for a person who
     * consented to [consentedScopes].
     */
    fun flowConfiguration(client: Client, consentedScopes: List<String>): ExpressionConfiguration =
        flowFunctions(client, consentedScopes)

    /**
     * Configuration for a door a token comes through, evaluated in [context] at [now].
     */
    fun tokenConfiguration(context: ClaimAclContext, now: LocalDateTime): ExpressionConfiguration =
        tokenFunctions(context, context.authenticationDate?.let { Duration.between(it, now) })

    /**
     * Refuse [expressionString] unless it is usable on the flow's door, and return what it names.
     *
     * One that is not throws an [InvalidRuleExpressionException] naming which of the two ways it failed; a
     * function of the token is an unknown function here, and a function taking a literal given anything else
     * does not parse.
     */
    fun validateFlowExpression(expressionString: String): ClaimAclExpressionTerms =
        validate(expressionString, flowFunctions(client = null, consentedScopes = emptyList()))

    /**
     * Refuse [expressionString] unless it is usable on a door a token comes through, and return what it
     * names, the same way and with the same failures as on the flow's.
     */
    fun validateTokenExpression(expressionString: String): ClaimAclExpressionTerms =
        validate(expressionString, tokenFunctions(context = null, authenticationAge = null))

    /**
     * The flow's function set, bound to the [client] and the [consentedScopes] of a flow, or to nothing where
     * the client is null.
     */
    private fun flowFunctions(client: Client?, consentedScopes: List<String>): ExpressionConfiguration =
        defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(client)),
            entry(ScopeIsConsentedFunction.FUNCTION_NAME, ScopeIsConsentedFunction(consentedScopes))
        )

    /**
     * The token doors' function set, bound to [context] and the [authenticationAge] behind it, or to nothing
     * where the context is null.
     */
    private fun tokenFunctions(context: ClaimAclContext?, authenticationAge: Duration?): ExpressionConfiguration {
        return defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(context?.client)),
            entry(
                ScopeIsConsentedFunction.FUNCTION_NAME,
                ScopeIsConsentedFunction(context?.consentedScopes ?: emptyList())
            ),
            entry(ScopeIsGrantedFunction.FUNCTION_NAME, ScopeIsGrantedFunction(context?.grantedScopes ?: emptyList())),
            entry(
                AuthenticationIsYoungerThanFunction.FUNCTION_NAME,
                AuthenticationIsYoungerThanFunction(authenticationAge)
            ),
            entry(TokenIsBoundFunction.FUNCTION_NAME, TokenIsBoundFunction(context?.bound ?: false))
        )
    }

    private fun validate(expressionString: String, configuration: ExpressionConfiguration): ClaimAclExpressionTerms {
        evaluateRuleExpression(expressionString, configuration)
        // Read off the tree rather than off an evaluation: `&&` and `||` stop at the first operand that
        // decides them, so a call an evaluation never reaches would go unread.
        val root = try {
            Expression(expressionString, configuration).abstractSyntaxTree
        } catch (e: ParseException) {
            throw invalidExpression(expressionString, e.message)
        }
        val calls = mutableListOf<Call>().also { collectCalls(root, opening = true, into = it) }

        fun Call.isOf(functionName: String) = node.token.value.equals(functionName, ignoreCase = true)
        fun literalsOf(calls: List<Call>, functionName: String): Set<String> = calls
            .filter { it.isOf(functionName) }
            .mapTo(mutableSetOf()) { literalArgumentOf(expressionString, it.node) }
        // Read here as well as by the function, so a duration in an operand no evaluation reaches is refused
        // too.
        fun durationsOf(calls: List<Call>): Set<Duration> =
            literalsOf(calls, AuthenticationIsYoungerThanFunction.FUNCTION_NAME).mapTo(mutableSetOf()) {
                AuthenticationIsYoungerThanFunction.readDurationOrNull(it)
                    ?: throw invalidExpression(expressionString, "$it is not a duration")
            }

        val openingCalls = calls.filter(Call::opening)
        return ClaimAclExpressionTerms(
            consentedScopes = literalsOf(calls, ScopeIsConsentedFunction.FUNCTION_NAME),
            grantedScopes = literalsOf(calls, ScopeIsGrantedFunction.FUNCTION_NAME),
            authenticationAges = durationsOf(calls),
            opening = ClaimAclOpeningTerms(
                consentedScopes = literalsOf(openingCalls, ScopeIsConsentedFunction.FUNCTION_NAME),
                authenticationAges = durationsOf(openingCalls),
                asksForBoundToken = openingCalls.any { it.isOf(TokenIsBoundFunction.FUNCTION_NAME) }
            )
        )
    }

    /**
     * A function call of an expression, and whether nothing but `&&` and `||` stands above it.
     */
    private class Call(val node: ASTNode, val opening: Boolean)

    /**
     * Collect every call under [node] into [into]. [opening] says whether nothing but `&&` and `||` stands
     * above [node]; a call's own arguments are under the call, so none of them is.
     */
    private fun collectCalls(node: ASTNode, opening: Boolean, into: MutableList<Call>) {
        val token = node.token
        if (token.type == TokenType.FUNCTION) into += Call(node, opening)
        val combinesOperands = token.type == TokenType.INFIX_OPERATOR && token.value in COMBINING_OPERATORS
        node.parameters.forEach { collectCalls(it, opening && combinesOperands, into) }
    }

    /**
     * The operators a condition stays a condition of the door's under.
     */
    private val COMBINING_OPERATORS = setOf("&&", "||")

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
