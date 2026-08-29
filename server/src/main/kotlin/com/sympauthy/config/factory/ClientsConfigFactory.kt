package com.sympauthy.config.factory

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope
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
    @Inject private val uncheckedFlowsConfig: AuthorizationFlowsConfig
) {

    @Singleton
    fun provideClients(
        propertiesList: List<ClientConfigurationProperties>
    ): Flow<ClientsConfig> {
        return flow {
            val templatesConfig = clientTemplatesConfig.orNull()
            if (templatesConfig == null) {
                emit(DisabledClientsConfig(emptyList()))
                return@flow
            }
            val audiencesConfig = uncheckedAudiencesConfig as? EnabledAudiencesConfig
            if (audiencesConfig == null) {
                emit(DisabledClientsConfig(emptyList()))
                return@flow
            }
            val scopesConfig = uncheckedScopesConfig.orNull()
            if (scopesConfig == null) {
                emit(DisabledClientsConfig(emptyList()))
                return@flow
            }
            val flowsConfig = uncheckedFlowsConfig as? EnabledAuthorizationFlowsConfig
            if (flowsConfig == null) {
                emit(DisabledClientsConfig(emptyList()))
                return@flow
            }

            val ctx = ConfigParsingContext()
            val parsed = clientsParser.parse(ctx, propertiesList, templatesConfig.templates)
            val clients = clientsValidator.validate(
                ctx, parsed,
                audiencesConfig.audiences.associateBy { it.id },
                scopesConfig.scopes.associateBy(Scope::scope),
                flowsConfig.flows.associateBy(AuthorizationFlow::id)
            )
            val config = if (ctx.hasErrors) DisabledClientsConfig(ctx.errors)
            else EnabledClientsConfig(clients)
            emit(config)
        }
    }
}
