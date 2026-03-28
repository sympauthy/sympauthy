package com.sympauthy.config.properties

import com.sympauthy.config.properties.ProviderConfigurationProperties.Companion.PROVIDERS_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration of a third-party authentication provider (ex. Discord).
 */
@EachProperty(PROVIDERS_KEY)
class ProviderConfigurationProperties(
    /**
     * Identifier of the provider.
     */
    @param:Parameter val id: String
) {
    /**
     * Display name of the client.
     */
    var name: String? = null

    /**
     * Configuration of display elements related to the client.
     * ex. url of the icon, colors used to display the client.
     */
    var ui: ClientUIConfig? = null

    /**
     *
     */
    var userInfo: UserInfoConfig? = null

    /**
     * To be used the client must support the authorization code grant type
     */
    var oauth2: Oauth2Config? = null

    /**
     * OpenID Connect configuration. When set, endpoints are auto-discovered from the issuer URL.
     */
    var oidc: OidcConfig? = null

    // Must be nested: https://github.com/micronaut-projects/micronaut-core/issues/2373
    @ConfigurationProperties("ui")
    interface ClientUIConfig {
        /**
         * CSS code of the color to use for the background when displaying a button redirecting
         * to the client login page.
         */
        val buttonBackground: String?

        /**
         * CSS code of the color to use as the text when displaying a button redirecting
         * to the client login page.
         */
        val buttonText: String?
    }

    // Only one level of nester properties are supported by Micronaut.
    @ConfigurationProperties("oauth2")
    interface Oauth2Config {
        val clientId: String?
        val clientSecret: String?
        val scopes: List<String>?

        val authorizationUrl: String?

        val tokenUrl: String?
        val tokenAuthMethod: String?
    }

    @ConfigurationProperties("oidc")
    interface OidcConfig {
        val issuer: String?
        val clientId: String?
        val clientSecret: String?
        val scopes: List<String>?
        val userinfoEnabled: Boolean?
    }

    @ConfigurationProperties("user-info")
    interface UserInfoConfig {
        val url: String?
        val paths: Map<String, String?>?
    }

    companion object {
        const val PROVIDERS_KEY = "providers"
    }
}
