package com.sympauthy.config.parsing

import com.sympauthy.business.model.rule.ActAsRuleBehavior
import com.sympauthy.business.model.rule.ActAsRuleBehavior.ALLOW
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.properties.ActAsRuleConfigurationProperties
import jakarta.inject.Singleton

data class ParsedActAsRule(
    val key: String,
    val userDefinedName: String?,
    val behavior: ActAsRuleBehavior?,
    val order: Int?,
    val expressions: List<String>?
)

@Singleton
class ActAsRulesConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        propertiesList: List<ActAsRuleConfigurationProperties>,
        rulesKey: String
    ): List<ParsedActAsRule> {
        return propertiesList.mapIndexed { index, properties ->
            parseRule(ctx, properties, "$rulesKey[$index]")
        }
    }

    private fun parseRule(
        ctx: ConfigParsingContext,
        properties: ActAsRuleConfigurationProperties,
        key: String
    ): ParsedActAsRule {
        val subCtx = ctx.child()

        val userDefinedName = subCtx.parse {
            parser.getString(properties, "$key.name", ActAsRuleConfigurationProperties::name)
        }

        val behavior = subCtx.parse {
            parser.getEnum<ActAsRuleConfigurationProperties, ActAsRuleBehavior>(
                properties, "$key.behavior", ALLOW,
                ActAsRuleConfigurationProperties::behavior
            )
        }

        val order = subCtx.parse {
            parser.getInt(properties, "$key.order", ActAsRuleConfigurationProperties::order)
        } ?: 0

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
        return ParsedActAsRule(
            key = key,
            userDefinedName = userDefinedName,
            behavior = behavior,
            order = order,
            expressions = expressions
        )
    }
}
