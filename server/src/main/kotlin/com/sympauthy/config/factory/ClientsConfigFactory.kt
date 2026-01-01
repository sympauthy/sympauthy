package com.sympauthy.config.factory

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClientsConfig
import com.sympauthy.config.model.DisabledClientsConfig
import com.sympauthy.config.model.EnabledClientsConfig
import com.sympauthy.config.properties.ClientConfigurationProperties
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.DEFAULT
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.URI

@Factory
class ClientsConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val scopeManager: ScopeManager,
) {

    @Singleton
    fun provideClients(
        propertiesList: List<ClientConfigurationProperties>
    ): Flow<ClientsConfig> {
        return flow {
            val errors = mutableListOf<ConfigurationException>()

            val defaultConfig = propertiesList.firstOrNull { it.id == DEFAULT }

            val clients = propertiesList
                .filter { it.id != DEFAULT }
                .mapNotNull { config ->
                    getClient(
                        properties = config,
                        defaultProperties = defaultConfig,
                        errors = errors
                    )
                }

            val config = if (errors.isEmpty()) {
                EnabledClientsConfig(clients)
            } else {
                DisabledClientsConfig(errors)
            }
            emit(config)
        }
    }

    private suspend fun getClient(
        properties: ClientConfigurationProperties,
        defaultProperties: ClientConfigurationProperties?,
        errors: MutableList<ConfigurationException>
    ): Client? {
        val secret = try {
            parser.getString(
                properties, "$CLIENTS_KEY.${properties.id}.secret",
                ClientConfigurationProperties::secret
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val authorizationFlow = try {
            getAuthorizationFlow(
                key = "$CLIENTS_KEY.${properties.id}.authorization-flow",
                flowId = properties.authorizationFlow ?: defaultProperties?.authorizationFlow
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val allowedRedirectUris = try {
            getAllowedRedirectUris(
                properties = properties,
                allowedRedirectUris = properties.allowedRedirectUris ?: defaultProperties?.allowedRedirectUris,
                errors = errors
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val allowedScopes = try {
            getScopes(
                key = "$CLIENTS_KEY.${properties.id}.allowed-scopes",
                scopes = properties.allowedScopes ?: defaultProperties?.allowedScopes,
                errors = errors
            )?.toSet()
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val defaultScopes = try {
            getScopes(
                key = "$CLIENTS_KEY.${properties.id}.default-scopes",
                scopes = properties.defaultScopes ?: defaultProperties?.defaultScopes,
                errors = errors
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            return Client(
                id = properties.id,
                secret = secret!!,
                authorizationFlow = authorizationFlow,
                allowedRedirectUris = allowedRedirectUris,
                allowedScopes = allowedScopes,
                defaultScopes = defaultScopes
            )
        } else null
    }

    private fun getAllowedRedirectUris(
        properties: ClientConfigurationProperties,
        allowedRedirectUris: List<String>?,
        errors: MutableList<ConfigurationException>
    ): List<URI>? {
        val listErrors = mutableListOf<ConfigurationException>()

        val allowedRedirectUris = allowedRedirectUris?.mapIndexedNotNull { index, uri ->
            try {
                parser.getAbsoluteUriOrThrow(
                    uri, "$CLIENTS_KEY.${properties.id}.allowed-redirect-uris[$index]"
                ) { it }
            } catch (e: ConfigurationException) {
                listErrors.add(e)
                null
            }
        }

        return if (listErrors.isEmpty()) {
            allowedRedirectUris
        } else {
            errors.addAll(listErrors)
            null
        }
    }

    private fun getAuthorizationFlow(
        key: String,
        flowId: String?
    ): AuthorizationFlow? {
        return flowId?.let {
            webAuthorizationFlowManager.findByIdOrNull(it) ?: throw configExceptionOf(
                "$key", "config.client.authorization_flow.invalid",
                "flow" to flowId
            )
        }
    }

    private suspend fun getScopes(
        key: String,
        scopes: List<String>?,
        errors: MutableList<ConfigurationException>
    ): List<Scope>? {
        val scopeErrors = mutableListOf<ConfigurationException>()

        val verifiedScopes = scopes?.mapIndexedNotNull { index, scope ->
            try {
                val verifiedScope = scopeManager.find(scope)
                if (verifiedScope == null) {
                    val error = configExceptionOf(
                        "$key[${index}]", "config.client.scope.invalid",
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
}
