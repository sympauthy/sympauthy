package com.sympauthy.business.model.redirect

import com.sympauthy.business.model.provider.config.ProviderOidcConfig
import io.micronaut.http.uri.UriBuilder
import java.net.URI

/**
 * Contains all the information required to generate the URI where the user must be redirected to initiate
 * an authentication to a third-party OIDC provider.
 */
data class ProviderOidcAuthorizationRedirect(
    val oidc: ProviderOidcConfig,
    val responseType: String,
    val state: String?,
    val redirectUri: URI?,
    val nonce: String,
) {

    fun build(): URI {
        val builder = UriBuilder.of(oidc.authorizationUri)
        responseType.let { builder.queryParam("response_type", it) }
        oidc.clientId.let { builder.queryParam("client_id", it) }
        oidc.scopes.joinToString(" ").let { builder.queryParam("scope", it) }
        state?.let { builder.queryParam("state", it) }
        redirectUri?.toString()?.let { builder.queryParam("redirect_uri", it) }
        builder.queryParam("nonce", nonce)
        return builder.build()
    }
}
