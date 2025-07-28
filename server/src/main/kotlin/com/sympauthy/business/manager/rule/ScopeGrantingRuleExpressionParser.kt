package com.sympauthy.business.manager.rule

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.parser.ParseException
import com.sympauthy.business.exception.InvalidScopeGrantingRuleBusinessException
import com.sympauthy.business.manager.rule.function.ClaimFunction
import com.sympauthy.business.manager.rule.function.ClaimIsVerifiedFunction
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Map.entry

/**
 * This factory is in charge of parsing and validation expression used in configured scope granting rules.
 *
 * SympAuthy relies on [EvalEx](https://github.com/ezylang/EvalEx) as its expression evaluator.
 * It provides some handy custom functions to help user to access info related to the current authentication flow
 * (ex. scopes, claims, etc.).
 */
@Singleton
class ScopeGrantingRuleExpressionParser(
    @Inject private val collectedClaimManager: CollectedClaimManager
) {

    /**
     * The [ExpressionConfiguration] without the custom functions and operator.
     */
    internal val defaultConfiguration = lazy {
        ExpressionConfiguration.defaultConfiguration()
    }

    /**
     * A dummy [ExpressionConfiguration] configured with non-working custom functions and operators allowing
     * the validation of scope granting rule during SympAuthy configuration.
     */
    internal val dummyConfiguration = lazy {
        defaultConfiguration.value
            .withAdditionalFunctions(
                entry(ClaimFunction.FUNCTION_NAME, ClaimFunction()),
                entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction())
            )
    }

    /**
     * Return the [ExpressionConfiguration] with the custom functions and operator configured with
     * the information relative to the [authorizeAttempt].
     */
    suspend fun getConfiguration(
        authorizeAttempt: AuthorizeAttempt
    ): ExpressionConfiguration {
        val collectedClaims = collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt)

        return defaultConfiguration.value
            .withAdditionalFunctions(
                entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(collectedClaims)),
                entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(collectedClaims))
            )
    }

    /**
     * Validate that the [expressionString] is a valid as a scope granting rule expression.
     * Throw a [ParseException] if the [expressionString] is not valid.
     *
     * Internally, this will cause the evaluation of the [expressionString] into a [Expression].
     * A [dummyConfiguration] will be passed to the [Expression] to make it aware of custom functions and operators.
     * Finally [Expression.validate] will be called causing the creation and validation of the abstract syntax tree.
     */
    suspend fun validateExpression(expressionString: String) {
        val expression = Expression(expressionString, dummyConfiguration.value)
        try {
            expression.validate()
        } catch (e: ParseException) {
            throw convertParseException(
                expressionString = expressionString,
                exception = e,
            )
        }
    }

    suspend fun evaluateExpression(
        expressionString: String,
        configuration: ExpressionConfiguration
    ): Boolean {
        val expression = Expression(expressionString, configuration)
        try {
            return expression.evaluate().booleanValue
        } catch (e: ParseException) {
            throw convertParseException(
                expressionString = expressionString,
                exception = e,
            )
        }
    }

    internal fun convertParseException(
        expressionString: String,
        exception: ParseException
    ): InvalidScopeGrantingRuleBusinessException {
        return TODO()
    }
}
