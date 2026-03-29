package com.sympauthy.business.model.provider.oauth2

import com.sympauthy.business.model.provider.ProviderCredentials
import io.micronaut.http.MutableHttpRequest

data class ProviderOAuth2Tokens(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String? = null
) : ProviderCredentials {

    override fun <T> authenticate(httpRequest: MutableHttpRequest<T>) {
        httpRequest.bearerAuth(accessToken)
    }
}
