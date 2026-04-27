package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.DisabledScopeGrantingRulesConfig
import com.sympauthy.config.model.EnabledScopeGrantingRulesConfig
import com.sympauthy.config.model.ScopeGrantingRulesConfig
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
    @Inject private val rulesValidator: ScopeGrantingRulesConfigValidator
) {

    @Singleton
    fun provideScopeGrantingRules(
        userPropertiesList: List<UserScopeGrantingRuleConfigurationProperties>,
        clientPropertiesList: List<ClientScopeGrantingRuleConfigurationProperties>
    ): Flow<ScopeGrantingRulesConfig> {
        return flow {
            val ctx = ConfigParsingContext()

            val parsedUserRules = rulesParser.parse(
                ctx, userPropertiesList, UserScopeGrantingRuleConfigurationProperties.RULES_KEY
            )
            val parsedClientRules = rulesParser.parse(
                ctx, clientPropertiesList, ClientScopeGrantingRuleConfigurationProperties.RULES_KEY
            )

            val userRules = rulesValidator.validateUserRules(ctx, parsedUserRules)
            val clientRules = rulesValidator.validateClientRules(ctx, parsedClientRules)

            if (ctx.hasErrors) {
                emit(DisabledScopeGrantingRulesConfig(ctx.errors))
            } else {
                emit(EnabledScopeGrantingRulesConfig(userRules, clientRules))
            }
        }
    }
}
