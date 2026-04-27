package com.sympauthy.config.validation

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.rule.InvalidScopeGrantingRuleException
import com.sympauthy.business.manager.rule.ScopeGrantingRuleExpressionExecutor
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.rule.ScopeGrantingRule
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedScopeGrantingRule
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ScopeGrantingRulesConfigValidator(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val scopeGrantingRuleExpressionExecutor: ScopeGrantingRuleExpressionExecutor
) {

    suspend fun validateUserRules(
        ctx: ConfigParsingContext,
        parsed: List<ParsedScopeGrantingRule>
    ): List<ScopeGrantingRule> {
        return parsed.mapNotNull { rule ->
            validateRule(ctx, rule, ::validateUserRuleScope, scopeGrantingRuleExpressionExecutor::validateUserExpression)
        }
    }

    suspend fun validateClientRules(
        ctx: ConfigParsingContext,
        parsed: List<ParsedScopeGrantingRule>
    ): List<ScopeGrantingRule> {
        return parsed.mapNotNull { rule ->
            validateRule(ctx, rule, ::validateClientRuleScope, scopeGrantingRuleExpressionExecutor::validateClientExpression)
        }
    }

    private suspend fun validateRule(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeGrantingRule,
        scopeValidator: (Scope, String, Int, ConfigParsingContext) -> Unit,
        expressionValidator: suspend (String) -> Unit
    ): ScopeGrantingRule? {
        val subCtx = ctx.child()

        // Validate scopes.
        val scopes = parsed.scopeIds?.mapIndexedNotNull { index, scopeId ->
            try {
                val scope = scopeManager.find(scopeId)
                if (scope == null) {
                    subCtx.addError(
                        configExceptionOf("${parsed.key}.scopes[$index]", "config.rule.scope.invalid", "scope" to scopeId)
                    )
                } else {
                    scopeValidator(scope, "${parsed.key}.scopes", index, subCtx)
                }
                scope
            } catch (_: Throwable) {
                null
            }
        }

        // Validate expressions.
        parsed.expressions?.forEachIndexed { index, expression ->
            try {
                expressionValidator(expression)
            } catch (e: InvalidScopeGrantingRuleException) {
                subCtx.addError(
                    configExceptionOf(
                        "${parsed.key}.expressions[$index]", e.configMessageId,
                        "message" to e.message
                    )
                )
            }
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || parsed.behavior == null || scopes == null || parsed.expressions == null) {
            return null
        }
        return ScopeGrantingRule(
            userDefinedName = parsed.userDefinedName,
            behavior = parsed.behavior,
            order = parsed.order ?: 0,
            scopes = scopes,
            expressions = parsed.expressions
        )
    }

    private fun validateUserRuleScope(scope: Scope, key: String, index: Int, ctx: ConfigParsingContext) {
        when (scope) {
            is ConsentableUserScope -> ctx.addError(
                configExceptionOf("$key[$index]", "config.rule.scope.consentable_not_allowed", "scope" to scope.scope)
            )
            is ClientScope -> ctx.addError(
                configExceptionOf("$key[$index]", "config.rule.scope.client_scope_not_allowed", "scope" to scope.scope)
            )
            is GrantableUserScope -> {} // Valid
        }
    }

    private fun validateClientRuleScope(scope: Scope, key: String, index: Int, ctx: ConfigParsingContext) {
        if (scope !is ClientScope) {
            ctx.addError(
                configExceptionOf("$key[$index]", "config.rule.scope.user_scope_not_allowed", "scope" to scope.scope)
            )
        }
    }
}
