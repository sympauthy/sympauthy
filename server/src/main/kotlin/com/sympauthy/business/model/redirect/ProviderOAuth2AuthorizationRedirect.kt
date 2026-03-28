package com.sympauthy.business.model.redirect

import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import io.micronaut.http.uri.UriBuilder
import java.net.URI

/**
 * Contains all the information required to generate the URI where the user must be redirected to initiate
 * an authentication to a third-party provider.
 */
data class ProviderOAuth2AuthorizationRedirect(
    val oauth2: ProviderOAuth2Config,
    val responseType: String,
    val state: String?,
    val redirectUri: URI?,
    val nonce: String? = null,
) {

    fun build(): URI {
        val builder = UriBuilder.of(oauth2.authorizationUri)
        responseType.let { builder.queryParam("response_type", it) }
        oauth2.clientId.let { builder.queryParam("client_id", it) }
        oauth2.scopes?.joinToString(" ")?.let { builder.queryParam("scope", it) }
        state?.let { builder.queryParam("state", it) }
        redirectUri?.toString()?.let { builder.queryParam("redirect_uri", it) }
        nonce?.let { builder.queryParam("nonce", it) }
        return builder.build()
    }
}
