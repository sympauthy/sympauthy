package com.sympauthy.config.validation

import com.sympauthy.business.manager.actas.ActAsRuleExpressionExecutor
import com.sympauthy.business.manager.actas.InvalidActAsRuleException
import com.sympauthy.business.model.rule.ActAsRule
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedActAsRule
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ActAsRulesConfigValidator(
    @Inject private val actAsRuleExpressionExecutor: ActAsRuleExpressionExecutor
) {

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
                actAsRuleExpressionExecutor.validateExpression(expression)
            } catch (e: InvalidActAsRuleException) {
                subCtx.addError(
                    configExceptionOf(
                        "${parsed.key}.expressions[$index]", e.configMessageId,
                        "message" to e.message
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
