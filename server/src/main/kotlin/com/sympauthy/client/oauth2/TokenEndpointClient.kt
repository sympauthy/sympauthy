package com.sympauthy.client.oauth2

import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2TokenRequest
import com.sympauthy.client.oauth2.model.TokenEndpointResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MediaType.APPLICATION_JSON
import io.micronaut.http.client.HttpClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirst

@Singleton
class TokenEndpointClient(
    @Inject private val httpClient: HttpClient
) {


    suspend fun fetchTokens(request: ProviderOAuth2TokenRequest): TokenEndpointResponse {
        val tokenUri = request.oauth2.tokenUri
        val body = mutableMapOf(
            "grant_type" to "authorization_code",
            "code" to request.authorizeCode,
            "redirect_uri" to request.redirectUri
        )

        val httpRequest = HttpRequest
            .POST(tokenUri, Map::class.java)
            .accept(APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .basicAuth(request.oauth2.clientId, request.oauth2.clientSecret)
            .body(body)

        return httpClient.retrieve(httpRequest, TokenEndpointResponse::class.java)
            .awaitFirst()
    }
}
