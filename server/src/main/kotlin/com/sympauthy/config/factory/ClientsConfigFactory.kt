package com.sympauthy.config.factory

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ClientsConfigParser
import com.sympauthy.config.properties.ClientConfigurationProperties
import com.sympauthy.config.validation.ClientsConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ClientsConfigFactory(
    @Inject private val clientsParser: ClientsConfigParser,
    @Inject private val clientsValidator: ClientsConfigValidator,
    @Inject private val clientTemplatesConfig: Flow<ClientTemplatesConfig>,
    @Inject private val uncheckedAudiencesConfig: AudiencesConfig,
    @Inject private val uncheckedScopesConfig: ScopesConfig,
    @Inject private val uncheckedFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Singleton
    fun provideClients(
        propertiesList: List<ClientConfigurationProperties>
    ): Flow<ClientsConfig> {
        return flow {
            // A client names an audience, scopes, a flow and the deployment's root URL. None of them can
            // be resolved from a configuration that did not build, so there is nothing to validate against.
            val templatesConfig = clientTemplatesConfig.orNull()
            val audiencesConfig = uncheckedAudiencesConfig as? EnabledAudiencesConfig
            val scopesConfig = uncheckedScopesConfig.orNull()
            val flowsConfig = uncheckedFlowsConfig as? EnabledAuthorizationFlowsConfig
            val rootUri = uncheckedUrlsConfig.getOrNull()?.root
            if (templatesConfig == null || audiencesConfig == null || scopesConfig == null ||
                flowsConfig == null || rootUri == null
            ) {
                emit(DisabledClientsConfig(emptyList()))
                return@flow
            }

            val ctx = ConfigParsingContext()
            val parsed = clientsParser.parse(ctx, propertiesList, templatesConfig.templates, rootUri)
            val clients = clientsValidator.validate(
                ctx, parsed,
                audiencesConfig.audiences.associateBy { it.id },
                scopesConfig.enabledScopes.associateBy(EnabledScope::scope),
                flowsConfig.flows.associateBy(AuthorizationFlow::id)
            )
            val config = if (ctx.hasErrors) DisabledClientsConfig(ctx.errors)
            else EnabledClientsConfig(clients)
            emit(config)
        }
    }
}
