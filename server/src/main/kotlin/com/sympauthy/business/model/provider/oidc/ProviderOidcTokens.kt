package com.sympauthy.business.model.provider.oidc

import com.sympauthy.business.model.provider.ProviderCredentials
import io.micronaut.http.MutableHttpRequest

data class ProviderOidcTokens(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String
) : ProviderCredentials {

    override fun <T> authenticate(httpRequest: MutableHttpRequest<T>) {
        httpRequest.bearerAuth(accessToken)
    }
}
