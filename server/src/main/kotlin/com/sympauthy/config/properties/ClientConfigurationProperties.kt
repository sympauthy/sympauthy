package com.sympauthy.config.properties

import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration of a client application that will authenticate its users.
 */
@EachProperty(CLIENTS_KEY)
class ClientConfigurationProperties(
    @param:Parameter val id: String
) {
    var template: String? = null
    var public: Boolean? = null
    var secret: String? = null
    var authorizationFlow: String? = null
    var uris: Map<String, String>? = null
    var allowedGrantTypes: List<String>? = null
    var allowedRedirectUris: List<String>? = null
    var allowedScopes: List<String>? = null
    var defaultScopes: List<String>? = null
    var authorizationWebhook: AuthorizationWebhookConfig? = null

    @ConfigurationProperties("authorization-webhook")
    interface AuthorizationWebhookConfig {
        val url: String?
        val secret: String?
        val onFailure: String?
    }

    companion object {
        const val CLIENTS_KEY = "clients"
    }
}
