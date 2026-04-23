package com.sympauthy.config.factory

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.rule.InvalidScopeGrantingRuleException
import com.sympauthy.business.manager.rule.ScopeGrantingRuleExpressionExecutor
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
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
import com.sympauthy.config.properties.ClientScopeGrantingRuleConfigurationProperties
import com.sympauthy.config.properties.ScopeGrantingRuleConfigurationProperties
import com.sympauthy.config.properties.UserScopeGrantingRuleConfigurationProperties
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ScopeGrantingRulesConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val scopeGrantingRuleExpressionExecutor: ScopeGrantingRuleExpressionExecutor
) {

    @Singleton
    fun provideScopeGrantingRules(
        userPropertiesList: List<UserScopeGrantingRuleConfigurationProperties>,
        clientPropertiesList: List<ClientScopeGrantingRuleConfigurationProperties>
    ): Flow<ScopeGrantingRulesConfig> {
        return flow {
            val errors = mutableListOf<ConfigurationException>()

            val userScopeGrantingRules = userPropertiesList.mapIndexedNotNull { index, properties ->
                getScopeGrantingRule(
                    properties = properties,
                    key = "${UserScopeGrantingRuleConfigurationProperties.RULES_KEY}[$index]",
                    errors = errors,
                    scopeValidator = ::validateUserRuleScope,
                    expressionValidator = scopeGrantingRuleExpressionExecutor::validateUserExpression
                )
            }

            val clientScopeGrantingRules = clientPropertiesList.mapIndexedNotNull { index, properties ->
                getScopeGrantingRule(
                    properties = properties,
                    key = "${ClientScopeGrantingRuleConfigurationProperties.RULES_KEY}[$index]",
                    errors = errors,
                    scopeValidator = ::validateClientRuleScope,
                    expressionValidator = scopeGrantingRuleExpressionExecutor::validateClientExpression
                )
            }

            if (errors.isEmpty()) {
                emit(EnabledScopeGrantingRulesConfig(userScopeGrantingRules, clientScopeGrantingRules))
            } else {
                emit(DisabledScopeGrantingRulesConfig(errors))
            }
        }
    }

    suspend fun getScopeGrantingRule(
        properties: ScopeGrantingRuleConfigurationProperties,
        key: String,
        errors: MutableList<ConfigurationException>,
        scopeValidator: (Scope, String, Int, MutableList<ConfigurationException>) -> Unit,
        expressionValidator: suspend (String) -> Unit
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
                errors = ruleErrors,
                scopeValidator = scopeValidator
            )
        } catch (e: ConfigurationException) {
            ruleErrors.add(e)
            null
        }

        val expressions = try {
            getExpressions(
                properties = properties,
                key = "$key.expressions",
                errors = ruleErrors,
                expressionValidator = expressionValidator
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

    private fun validateUserRuleScope(
        scope: Scope,
        key: String,
        index: Int,
        errors: MutableList<ConfigurationException>
    ) {
        val scopeString = scope.scope
        when (scope) {
            is ConsentableUserScope -> errors.add(
                configExceptionOf(
                    "$key[$index]", "config.rule.scope.consentable_not_allowed",
                    "scope" to scopeString
                )
            )

            is ClientScope -> errors.add(
                configExceptionOf(
                    "$key[$index]", "config.rule.scope.client_scope_not_allowed",
                    "scope" to scopeString
                )
            )

            is GrantableUserScope -> {} // Valid
        }
    }

    private fun validateClientRuleScope(
        scope: Scope,
        key: String,
        index: Int,
        errors: MutableList<ConfigurationException>
    ) {
        if (scope !is ClientScope) {
            errors.add(
                configExceptionOf(
                    "$key[$index]", "config.rule.scope.user_scope_not_allowed",
                    "scope" to scope.scope
                )
            )
        }
    }

    private suspend fun getScopes(
        properties: ScopeGrantingRuleConfigurationProperties,
        key: String,
        errors: MutableList<ConfigurationException>,
        scopeValidator: (Scope, String, Int, MutableList<ConfigurationException>) -> Unit
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
                    scopeErrors.add(
                        configExceptionOf(
                            "$key[${index}]", "config.rule.scope.invalid",
                            "scope" to scope
                        )
                    )
                } else {
                    scopeValidator(verifiedScope, key, index, scopeErrors)
                }
                verifiedScope
            } catch (t: Throwable) {
                // We do not add the error to the list since it is most likely already caused by another configuration error
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
        errors: MutableList<ConfigurationException>,
        expressionValidator: suspend (String) -> Unit
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
                    index = index,
                    expressionValidator = expressionValidator
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
        expressionValidator: suspend (String) -> Unit
    ): String {
        val expression = parser.getStringOrThrow(
            properties, key,
            { properties.expressions?.getOrNull(index) }
        )
        try {
            expressionValidator(expression)
        } catch (e: InvalidScopeGrantingRuleException) {
            throw configExceptionOf(
                key, e.configMessageId,
                "message" to e.message
            )
        }
        return expression
    }
}
