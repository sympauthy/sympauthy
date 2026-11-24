package com.sympauthy.business.provider.oauth2.model

import com.sympauthy.business.provider.model.ProviderCredentials
import io.micronaut.http.MutableHttpRequest

data class ProviderOAuth2Tokens(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String? = null
) : ProviderCredentials {

    override fun <T : Any> authenticate(httpRequest: MutableHttpRequest<T>) {
        httpRequest.bearerAuth(accessToken)
    }
}
