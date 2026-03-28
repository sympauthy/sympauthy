package com.sympauthy.business.model.redirect

import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import io.micronaut.http.uri.UriBuilder
import java.net.URI

/**
 * Contains all the information required to generate the URI where the user must be redirected to initiate
 * an authentication to a third-party OpenID Connect provider.
 */
data class ProviderOpenIdConnectAuthorizationRedirect(
    val openIdConnect: ProviderOpenIdConnectConfig,
    val responseType: String,
    val state: String?,
    val redirectUri: URI?,
    val nonce: String,
) {

    fun build(): URI {
        val builder = UriBuilder.of(openIdConnect.authorizationUri)
        responseType.let { builder.queryParam("response_type", it) }
        openIdConnect.clientId.let { builder.queryParam("client_id", it) }
        openIdConnect.scopes.joinToString(" ").let { builder.queryParam("scope", it) }
        state?.let { builder.queryParam("state", it) }
        redirectUri?.toString()?.let { builder.queryParam("redirect_uri", it) }
        builder.queryParam("nonce", nonce)
        return builder.build()
    }
}