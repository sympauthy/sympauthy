package com.sympauthy.config.properties

import com.sympauthy.config.properties.ClientConfigurationProperties.AuthorizationWebhookConfig
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration of a client template that defines default values for client applications.
 *
 * Templates named "default" are automatically applied to all clients that do not specify an explicit template.
 * Custom templates can be referenced by name via the `template` property on a client.
 */
@EachProperty(ClientTemplateConfigurationProperties.TEMPLATES_CLIENTS_KEY)
class ClientTemplateConfigurationProperties(
    @param:Parameter val id: String
) {
    var audience: String? = null
    var public: Boolean? = null
    var authorizationFlow: String? = null
    var uris: Map<String, String>? = null
    var allowedGrantTypes: List<String>? = null
    var allowedRedirectUris: List<String>? = null
    var allowedScopes: List<String>? = null
    var defaultScopes: List<String>? = null
    var authorizationWebhook: AuthorizationWebhookConfig? = null

    companion object {
        const val TEMPLATES_CLIENTS_KEY = "templates.clients"

        /**
         * Name of the default client template that is automatically applied to all clients
         * that do not specify an explicit template.
         */
        const val DEFAULT = "default"
    }
}
