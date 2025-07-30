package com.sympauthy.config.factory

import com.sympauthy.business.exception.InvalidScopeGrantingRuleBusinessException
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleExpressionParser
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.rule.ScopeGrantingRule
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.GRANT
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.DisabledScopeGrantingRulesConfig
import com.sympauthy.config.model.EnabledScopeGrantingRulesConfig
import com.sympauthy.config.model.ScopeGrantingRulesConfig
import com.sympauthy.config.properties.ScopeGrantingRuleConfigurationProperties
import com.sympauthy.config.properties.ScopeGrantingRuleConfigurationProperties.Companion.RULES_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ScopeGrantingRulesConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val scopeGrantingRuleExpressionParser: ScopeGrantingRuleExpressionParser
) {

    @Singleton
    fun provideScopeGrantingRules(
        propertiesList: List<ScopeGrantingRuleConfigurationProperties>
    ): Flow<ScopeGrantingRulesConfig> {
        return flow {
            val errors = mutableListOf<ConfigurationException>()
            val scopeGrantingRules = propertiesList.mapIndexedNotNull { index, properties ->
                val rule = getScopeGrantingRules(
                    properties = properties,
                    key = "$RULES_KEY[$index]",
                    errors = errors,
                )
                rule
            }

            if (errors.isEmpty()) {
                emit(EnabledScopeGrantingRulesConfig(scopeGrantingRules))
            } else {
                emit(DisabledScopeGrantingRulesConfig(errors))
            }
        }
    }

    suspend fun getScopeGrantingRules(
        properties: ScopeGrantingRuleConfigurationProperties,
        key: String,
        errors: MutableList<ConfigurationException>
    ): ScopeGrantingRule? {
        val ruleErrors = mutableListOf<ConfigurationException>()

        val userDefinedName = try {
            parser.getString(
                properties, "$key.name",
                ScopeGrantingRuleConfigurationProperties::name
            )
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val behavior = try {
            parser.getEnum<ScopeGrantingRuleConfigurationProperties, ScopeGrantingRuleBehavior>(
                properties, "$key.behavior", GRANT,
                ScopeGrantingRuleConfigurationProperties::behavior
            )
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val order = try {
            parser.getInt(
                properties, "$key.order",
                ScopeGrantingRuleConfigurationProperties::order
            ) ?: 0
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val scopes = try {
            getScopes(
                properties = properties,
                key = "$key.scopes",
                errors = ruleErrors
            )
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val expressions = try {
            getExpressions(
                properties = properties,
                key = "$key.expressions",
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

    private suspend fun getScopes(
        properties: ScopeGrantingRuleConfigurationProperties,
        key: String,
        errors: MutableList<ConfigurationException>
    ): List<Scope>? {
        val scopeErrors = mutableListOf<ConfigurationException>()

        if (properties.scopes.isNullOrEmpty()) {
            errors.add(configExceptionOf(key, "config.empty"))
            return null
        }

        val verifiedScopes = properties.scopes?.mapIndexedNotNull { index, scope ->
            try {
                val verifiedScope = scopeManager.find(scope)
                if (verifiedScope == null) {
                    val error = configExceptionOf(
                        "$key[${index}]", "config.rule.scope.invalid",
                        "scope" to scope
                    )
                    scopeErrors.add(error)
                }
                verifiedScope
            } catch (t: Throwable) {
                // We do not had the error to the list since it is most likely already caused by another configuration error
                null
            }
        }

        return if (scopeErrors.isEmpty()) {
            verifiedScopes
        } else {
            errors.addAll(scopeErrors)
            null
        }
    }

    private suspend fun getExpressions(
        properties: ScopeGrantingRuleConfigurationProperties,
        key: String,
        errors: MutableList<ConfigurationException>
    ): List<String>? {
        if (properties.expressions.isNullOrEmpty()) {
            errors.add(configExceptionOf(key, "config.empty"))
            return null
        }

        return properties.expressions?.mapIndexedNotNull { index, _ ->
            try {
                getExpression(
                    properties = properties,
                    key = "$key[$index]",
                    index = index
                )
            } catch (e: ConfigurationException) {
                errors.add(e)
                null
            }
        }
    }

    private suspend fun getExpression(
        properties: ScopeGrantingRuleConfigurationProperties,
        key: String,
        index: Int,
    ): String? {
        val expression = parser.getStringOrThrow(
            properties, key,
            { properties.expressions?.getOrNull(index) }
        )
        try {
            scopeGrantingRuleExpressionParser.validateExpression(expression)
        } catch (e: InvalidScopeGrantingRuleBusinessException) {
            throw configExceptionOf(
                key, "config.rule.expression.invalid",
                "message" to e.message
            )
        }
        return expression
    }
}
