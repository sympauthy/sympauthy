package com.sympauthy.business.manager.actas

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue.DataType.BOOLEAN
import com.ezylang.evalex.parser.ParseException
import com.sympauthy.business.manager.rule.function.ClaimFunction
import com.sympauthy.business.manager.rule.function.ClaimIsVerifiedFunction
import com.sympauthy.business.manager.rule.function.ClientFunction
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.CollectedClaim
import jakarta.inject.Singleton
import java.util.Map.entry

/**
 * This manager is in charge of evaluating the act-as rule expressions.
 *
 * An act-as expression may reference both the acting [Client] (via the `CLIENT` function) and the target user
 * (via the `CLAIM` / `CLAIM_IS_VERIFIED` functions), so all these functions are registered together in a single
 * [ExpressionConfiguration].
 */
@Singleton
class ActAsRuleExpressionExecutor {

    /**
     * The [ExpressionConfiguration] without the custom functions and operator.
     */
    internal val defaultConfiguration = lazy {
        ExpressionConfiguration.defaultConfiguration()
    }

    /**
     * A dummy [ExpressionConfiguration] configured with non-working custom functions allowing
     * the validation of act-as rules during SympAuthy configuration.
     */
    internal val dummyConfiguration = lazy {
        defaultConfiguration.value
            .withAdditionalFunctions(
                entry(ClientFunction.FUNCTION_NAME, ClientFunction()),
                entry(ClaimFunction.FUNCTION_NAME, ClaimFunction()),
                entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction())
            )
    }

    /**
     * Return the [ExpressionConfiguration] with the custom functions configured for act-as rules:
     * the acting [client] (via [ClientFunction]) and the target user's [targetUserClaims]
     * (via [ClaimFunction] / [ClaimIsVerifiedFunction]).
     */
    fun getConfiguration(
        client: Client,
        targetUserClaims: List<CollectedClaim>
    ): ExpressionConfiguration {
        return defaultConfiguration.value
            .withAdditionalFunctions(
                entry(ClientFunction.FUNCTION_NAME, ClientFunction(client)),
                entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(targetUserClaims)),
                entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(targetUserClaims))
            )
    }

    /**
     * Validate that the [expressionString] is a valid act-as rule expression.
     * Throw a [InvalidActAsRuleException] if the [expressionString] is not valid.
     */
    suspend fun validateExpression(expressionString: String) {
        evaluateExpressionOrThrow(expressionString, dummyConfiguration.value)
    }

    /**
     * Evaluates a given expression string using the provided [configuration].
     * If the evaluation fails due to a parsing error or the result is not of type Boolean,
     * an [InvalidActAsRuleException] is thrown.
     */
    internal suspend fun evaluateExpressionOrThrow(
        expressionString: String,
        configuration: ExpressionConfiguration
    ): Boolean {
        val expression = Expression(expressionString, configuration)
        val value = try {
            expression.evaluate()
        } catch (e: ParseException) {
            throw InvalidActAsRuleException(
                expressionString = expressionString,
                configMessageId = "config.rule.expression.invalid",
                businessErrorDetailsId = "rule.evaluate.failed",
                message = e.message,
            )
        }
        if (value.dataType != BOOLEAN) {
            throw InvalidActAsRuleException(
                expressionString = expressionString,
                configMessageId = "config.rule.expression.invalid_return",
                businessErrorDetailsId = "rule.evaluate.invalid_return",
            )
        }
        return value.booleanValue
    }
}
