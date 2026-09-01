package com.sympauthy.config.factory

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.DisabledClientTemplatesConfig
import com.sympauthy.config.model.EnabledAuthorizationFlowsConfig
import com.sympauthy.config.model.EnabledClientTemplatesConfig
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getOrNull
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
    @Inject private val uncheckedFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Singleton
    fun provideClientTemplates(
        templatesList: List<ClientTemplateConfigurationProperties>
    ): Flow<ClientTemplatesConfig> {
        return flow {
            val scopesConfig = uncheckedScopesConfig.orNull()
            val flowsConfig = uncheckedFlowsConfig as? EnabledAuthorizationFlowsConfig
            val rootUri = uncheckedUrlsConfig.getOrNull()?.root
            if (scopesConfig == null || flowsConfig == null || rootUri == null) {
                emit(DisabledClientTemplatesConfig(emptyList()))
                return@flow
            }

            val ctx = ConfigParsingContext()
            val parsed = clientTemplatesParser.parse(ctx, templatesList, rootUri)
            val templates = clientTemplatesValidator.validate(
                ctx, parsed,
                scopesConfig.enabledScopes.associateBy(EnabledScope::scope),
                flowsConfig.flows.associateBy(AuthorizationFlow::id)
            )
            val config = if (ctx.hasErrors) DisabledClientTemplatesConfig(ctx.errors)
            else EnabledClientTemplatesConfig(templates)
            emit(config)
        }
    }
}
