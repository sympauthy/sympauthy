package com.sympauthy.business.manager.rule

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue.DataType.BOOLEAN
import com.ezylang.evalex.parser.ParseException
import com.sympauthy.business.manager.rule.function.ClaimFunction
import com.sympauthy.business.manager.rule.function.ClaimIsVerifiedFunction
import com.sympauthy.business.manager.rule.function.ClientFunction
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaim
import jakarta.inject.Singleton
import java.util.Map.entry

/**
 * This manager is in charge of evaluating the scope granting rules expressions.
 *
 * SympAuthy relies on [EvalEx](https://github.com/ezylang/EvalEx) as its expression evaluator.
 * It provides some handy custom functions to help user to access info related to the current authentication flow
 * (ex. scopes, claims, etc.).
 */
@Singleton
class ScopeGrantingRuleExpressionExecutor {

    /**
     * The [ExpressionConfiguration] without the custom functions and operator.
     */
    internal val defaultConfiguration = lazy {
        ExpressionConfiguration.defaultConfiguration()
    }

    /**
     * A dummy [ExpressionConfiguration] configured with non-working custom functions allowing
     * the validation of user scope granting rules during SympAuthy configuration.
     */
    internal val userDummyConfiguration = lazy {
        defaultConfiguration.value
            .withAdditionalFunctions(
                entry(ClaimFunction.FUNCTION_NAME, ClaimFunction()),
                entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction())
            )
    }

    /**
     * A dummy [ExpressionConfiguration] configured with non-working custom functions allowing
     * the validation of client scope granting rules during SympAuthy configuration.
     */
    internal val clientDummyConfiguration = lazy {
        defaultConfiguration.value
            .withAdditionalFunctions(
                entry(ClientFunction.FUNCTION_NAME, ClientFunction())
            )
    }

    /**
     * Return the [ExpressionConfiguration] with the custom functions configured with
     * the information relative to the [authorizeAttempt] for user scope granting rules.
     */
    suspend fun getConfiguration(
        authorizeAttempt: AuthorizeAttempt,
        collectedClaims: List<CollectedClaim>
    ): ExpressionConfiguration {
        return defaultConfiguration.value
            .withAdditionalFunctions(
                entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(collectedClaims)),
                entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(collectedClaims))
            )
    }

    /**
     * Return the [ExpressionConfiguration] with the custom functions configured with
     * the information relative to the [client] for client scope granting rules.
     */
    fun getClientConfiguration(client: Client): ExpressionConfiguration {
        return defaultConfiguration.value
            .withAdditionalFunctions(
                entry(ClientFunction.FUNCTION_NAME, ClientFunction(client))
            )
    }

    /**
     * Validate that the [expressionString] is a valid user scope granting rule expression.
     * Throw a [InvalidScopeGrantingRuleException] if the [expressionString] is not valid.
     */
    suspend fun validateUserExpression(expressionString: String) {
        evaluateExpressionOrThrow(expressionString, userDummyConfiguration.value)
    }

    /**
     * Validate that the [expressionString] is a valid client scope granting rule expression.
     * Throw a [InvalidScopeGrantingRuleException] if the [expressionString] is not valid.
     */
    suspend fun validateClientExpression(expressionString: String) {
        evaluateExpressionOrThrow(expressionString, clientDummyConfiguration.value)
    }

    /**
     * Evaluates a given expression string using the provided [configuration].
     * If the evaluation fails due to a parsing error or the result is not of type Boolean,
     * an [InvalidScopeGrantingRuleException] is thrown.
     */
    internal suspend fun evaluateExpressionOrThrow(
        expressionString: String,
        configuration: ExpressionConfiguration
    ): Boolean {
        val expression = Expression(expressionString, configuration)
        val value = try {
            expression.evaluate()
        } catch (e: ParseException) {
            throw InvalidScopeGrantingRuleException(
                expressionString = expressionString,
                configMessageId = "config.rule.expression.invalid",
                businessErrorDetailsId = "rule.evaluate.failed",
                message = e.message,
            )
        }
        if (value.dataType != BOOLEAN) {
            throw InvalidScopeGrantingRuleException(
                expressionString = expressionString,
                configMessageId = "config.rule.expression.invalid_return",
                businessErrorDetailsId = "rule.evaluate.invalid_return",
            )
        }
        return value.booleanValue
    }
}
