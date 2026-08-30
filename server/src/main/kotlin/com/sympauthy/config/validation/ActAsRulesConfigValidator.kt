package com.sympauthy.config.validation

import com.sympauthy.business.model.rule.ActAsRule
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedActAsRule
import com.sympauthy.expression.ActAsRuleExpressions
import com.sympauthy.expression.InvalidRuleExpressionException
import jakarta.inject.Singleton

@Singleton
class ActAsRulesConfigValidator {

    suspend fun validateActAsRules(
        ctx: ConfigParsingContext,
        parsed: List<ParsedActAsRule>
    ): List<ActAsRule> {
        return parsed.mapNotNull { rule ->
            validateRule(ctx, rule)
        }
    }

    private suspend fun validateRule(
        ctx: ConfigParsingContext,
        parsed: ParsedActAsRule
    ): ActAsRule? {
        val subCtx = ctx.child()

        parsed.expressions?.forEachIndexed { index, expression ->
            try {
                ActAsRuleExpressions.validateExpression(expression)
            } catch (e: InvalidRuleExpressionException) {
                subCtx.addError(
                    configExceptionOf(
                        "${parsed.key}.expressions[$index]", e.configMessageId,
                        "message" to e.reason
                    )
                )
            }
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || parsed.behavior == null || parsed.expressions == null) {
            return null
        }
        return ActAsRule(
            userDefinedName = parsed.userDefinedName,
            behavior = parsed.behavior,
            order = parsed.order ?: 0,
            expressions = parsed.expressions
        )
    }
}
