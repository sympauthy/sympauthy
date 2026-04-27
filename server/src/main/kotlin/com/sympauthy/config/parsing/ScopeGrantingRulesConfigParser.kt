package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.properties.ScopeGrantingRuleConfigurationProperties
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.GRANT
import jakarta.inject.Singleton

data class ParsedScopeGrantingRule(
    val key: String,
    val userDefinedName: String?,
    val behavior: ScopeGrantingRuleBehavior?,
    val order: Int?,
    val scopeIds: List<String>?,
    val expressions: List<String>?
)

@Singleton
class ScopeGrantingRulesConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        propertiesList: List<ScopeGrantingRuleConfigurationProperties>,
        rulesKey: String
    ): List<ParsedScopeGrantingRule> {
        return propertiesList.mapIndexed { index, properties ->
            parseRule(ctx, properties, "$rulesKey[$index]")
        }
    }

    private fun parseRule(
        ctx: ConfigParsingContext,
        properties: ScopeGrantingRuleConfigurationProperties,
        key: String
    ): ParsedScopeGrantingRule {
        val subCtx = ctx.child()

        val userDefinedName = subCtx.parse {
            parser.getString(properties, "$key.name", ScopeGrantingRuleConfigurationProperties::name)
        }

        val behavior = subCtx.parse {
            parser.getEnum<ScopeGrantingRuleConfigurationProperties, ScopeGrantingRuleBehavior>(
                properties, "$key.behavior", GRANT,
                ScopeGrantingRuleConfigurationProperties::behavior
            )
        }

        val order = subCtx.parse {
            parser.getInt(properties, "$key.order", ScopeGrantingRuleConfigurationProperties::order)
        } ?: 0

        if (properties.scopes.isNullOrEmpty()) {
            subCtx.addError(configExceptionOf("$key.scopes", "config.empty"))
        }

        if (properties.expressions.isNullOrEmpty()) {
            subCtx.addError(configExceptionOf("$key.expressions", "config.empty"))
        }

        val expressions = properties.expressions?.mapIndexedNotNull { index, _ ->
            subCtx.parse {
                parser.getStringOrThrow(properties, "$key.expressions[$index]") {
                    properties.expressions?.getOrNull(index)
                }
            }
        }

        ctx.merge(subCtx)
        return ParsedScopeGrantingRule(
            key = key,
            userDefinedName = userDefinedName,
            behavior = behavior,
            order = order,
            scopeIds = properties.scopes,
            expressions = expressions
        )
    }
}
