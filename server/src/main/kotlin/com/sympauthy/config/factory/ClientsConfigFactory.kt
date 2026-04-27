package com.sympauthy.config.factory

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
    @Inject private val uncheckedAudiencesConfig: AudiencesConfig
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

            val ctx = ConfigParsingContext()
            val parsed = clientsParser.parse(ctx, propertiesList, templatesConfig.templates)
            val clients = clientsValidator.validate(
                ctx, parsed, audiencesConfig.audiences.associateBy { it.id }
            )
            val config = if (ctx.hasErrors) DisabledClientsConfig(ctx.errors)
            else EnabledClientsConfig(clients)
            emit(config)
        }
    }
}
