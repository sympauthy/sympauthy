package com.sympauthy.config.factory

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.DisabledClientTemplatesConfig
import com.sympauthy.config.model.EnabledAuthorizationFlowsConfig
import com.sympauthy.config.model.EnabledClientTemplatesConfig
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.orNull
import com.sympauthy.config.parsing.ClientTemplatesConfigParser
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties
import com.sympauthy.config.validation.ClientTemplatesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ClientTemplatesConfigFactory(
    @Inject private val clientTemplatesParser: ClientTemplatesConfigParser,
    @Inject private val clientTemplatesValidator: ClientTemplatesConfigValidator,
    @Inject private val uncheckedScopesConfig: ScopesConfig,
    @Inject private val uncheckedFlowsConfig: AuthorizationFlowsConfig
) {

    @Singleton
    fun provideClientTemplates(
        templatesList: List<ClientTemplateConfigurationProperties>
    ): Flow<ClientTemplatesConfig> {
        return flow {
            val scopesConfig = uncheckedScopesConfig.orNull()
            if (scopesConfig == null) {
                emit(DisabledClientTemplatesConfig(emptyList()))
                return@flow
            }
            val flowsConfig = uncheckedFlowsConfig as? EnabledAuthorizationFlowsConfig
            if (flowsConfig == null) {
                emit(DisabledClientTemplatesConfig(emptyList()))
                return@flow
            }

            val ctx = ConfigParsingContext()
            val parsed = clientTemplatesParser.parse(ctx, templatesList)
            val templates = clientTemplatesValidator.validate(
                ctx, parsed,
                scopesConfig.scopes.associateBy(Scope::scope),
                flowsConfig.flows.associateBy(AuthorizationFlow::id)
            )
            val config = if (ctx.hasErrors) DisabledClientTemplatesConfig(ctx.errors)
            else EnabledClientTemplatesConfig(templates)
            emit(config)
        }
    }
}
