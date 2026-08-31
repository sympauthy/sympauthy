package com.sympauthy.config.factory

import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.DisabledScopeGrantingRulesConfig
import com.sympauthy.config.model.EnabledScopeGrantingRulesConfig
import com.sympauthy.config.model.ScopeGrantingRulesConfig
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.orNull
import com.sympauthy.config.parsing.ScopeGrantingRulesConfigParser
import com.sympauthy.config.properties.ClientScopeGrantingRuleConfigurationProperties
import com.sympauthy.config.properties.UserScopeGrantingRuleConfigurationProperties
import com.sympauthy.config.validation.ScopeGrantingRulesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ScopeGrantingRulesConfigFactory(
    @Inject private val rulesParser: ScopeGrantingRulesConfigParser,
    @Inject private val rulesValidator: ScopeGrantingRulesConfigValidator,
    @Inject private val uncheckedScopesConfig: ScopesConfig
) {

    @Singleton
    fun provideScopeGrantingRules(
        userPropertiesList: List<UserScopeGrantingRuleConfigurationProperties>,
        clientPropertiesList: List<ClientScopeGrantingRuleConfigurationProperties>
    ): Flow<ScopeGrantingRulesConfig> {
        return flow {
            val scopesConfig = uncheckedScopesConfig.orNull()
            if (scopesConfig == null) {
                emit(DisabledScopeGrantingRulesConfig(emptyList()))
                return@flow
            }
            val scopesById = scopesConfig.scopes.associateBy(EnabledScope::scope)

            val ctx = ConfigParsingContext()

            val parsedUserRules = rulesParser.parse(
                ctx, userPropertiesList, UserScopeGrantingRuleConfigurationProperties.RULES_KEY
            )
            val parsedClientRules = rulesParser.parse(
                ctx, clientPropertiesList, ClientScopeGrantingRuleConfigurationProperties.RULES_KEY
            )

            val userRules = rulesValidator.validateUserRules(ctx, parsedUserRules, scopesById)
            val clientRules = rulesValidator.validateClientRules(ctx, parsedClientRules, scopesById)

            if (ctx.hasErrors) {
                emit(DisabledScopeGrantingRulesConfig(ctx.errors))
            } else {
                emit(EnabledScopeGrantingRulesConfig(userRules, clientRules))
            }
        }
    }
}
