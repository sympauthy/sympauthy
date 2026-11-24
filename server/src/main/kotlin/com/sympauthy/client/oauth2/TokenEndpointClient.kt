package com.sympauthy.client.oauth2

import com.sympauthy.business.provider.oauth2.model.ProviderOAuth2TokenRequest
import com.sympauthy.client.oauth2.model.TokenEndpointResponse
import com.sympauthy.client.remoteResponseOrNull
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

/**
 * The error a token endpoint refuses an authorization code with (RFC 6749 §5.2).
 */
const val INVALID_GRANT = "invalid_grant"

/**
 * The `error` a token endpoint named in the error response this failure carries (RFC 6749 §5.2), or null where
 * it answered no `4xx`, or one whose body is not such a response.
 *
 * Reading the body never throws: this is read while a failure is already being handled, and an answer that is
 * not JSON — the page a proxy answers with — is one with no error named.
 */
fun Throwable.tokenEndpointErrorOrNull(): String? {
    val response = remoteResponseOrNull() ?: return null
    if (response.code() !in 400..499) return null
    val body = try {
        response.response.getBody(Map::class.java).orElse(null)
    } catch (_: Exception) {
        null
    }
    return body?.get("error") as? String
}
