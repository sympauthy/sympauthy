package com.sympauthy.config.factory

import com.ezylang.evalex.Expression
import com.sympauthy.business.manager.rule.ScopeGrantingRuleExpressionParser
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.rule.ScopeGrantingRule
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.DisabledScopeGrantingRulesConfig
import com.sympauthy.config.model.EnabledScopeGrantingRulesConfig
import com.sympauthy.config.model.ScopeGrantingRulesConfig
import com.sympauthy.config.properties.ScopeGrantingRuleConfigurationProperties
import com.sympauthy.config.properties.ScopeGrantingRuleConfigurationProperties.Companion.RULES_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ScopeGrantingRulesConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val scopeGrantingRuleExpressionParser: ScopeGrantingRuleExpressionParser
) {

    @Singleton
    fun provideScopeGrantingRules(
        propertiesList: List<ScopeGrantingRuleConfigurationProperties>
    ): ScopeGrantingRulesConfig {
        val errors = mutableListOf<ConfigurationException>()
        val scopeGrantingRules = propertiesList.mapIndexedNotNull { index, properties ->
            getScopeGrantingRules(
                index = index,
                properties = properties,
                errors = errors,
            )
        }

        return if (errors.isEmpty()) {
            EnabledScopeGrantingRulesConfig(scopeGrantingRules)
        } else {
            DisabledScopeGrantingRulesConfig(errors)
        }
    }

    fun getScopeGrantingRules(
        index: Int,
        properties: ScopeGrantingRuleConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): ScopeGrantingRule? {
        val ruleErrors = mutableListOf<ConfigurationException>()

        val userDefinedName = try {
            parser.getString(
                properties, "$RULES_KEY.$index.name",
                ScopeGrantingRuleConfigurationProperties::name
            )
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val behavior = try {
            parser.getEnumOrThrow<ScopeGrantingRuleConfigurationProperties, ScopeGrantingRuleBehavior>(
                properties, "$RULES_KEY.$index.behavior",
                ScopeGrantingRuleConfigurationProperties::behavior
            )
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val order = try {
            parser.getInt(
                properties, "$RULES_KEY.$index.order",
                ScopeGrantingRuleConfigurationProperties::order
            ) ?: 0
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val scopes = try {
            getScopes(
                properties = properties,
                errors = ruleErrors
            )
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val expressions = try {
            getExpressions(
                properties = properties,
                errors = ruleErrors
            )
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        return if (ruleErrors.isEmpty()) {
            ScopeGrantingRule(
                userDefinedName = userDefinedName,
                behavior = behavior!!,
                order = order!!,
                scopes = scopes!!,
                expressions = expressions!!
            )
        } else {
            errors.addAll(ruleErrors)
            null
        }
    }

    private fun getScopes(
        properties: ScopeGrantingRuleConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): List<Scope> {
        return emptyList() // FIXME
    }

    private fun getExpressions(
        properties: ScopeGrantingRuleConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): List<Expression> {
        return emptyList() // FIXME
    }
}
