package com.sympauthy.client.oidc

import com.sympauthy.client.oidc.model.OidcDiscoveryResponse
import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType.APPLICATION_JSON
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirst
import java.net.URI

@Singleton
class OidcDiscoveryClient(
    @Inject private val httpClient: HttpClient
) {

    suspend fun fetchDiscovery(issuerUri: URI): OidcDiscoveryResponse {
        val discoveryUri = UriBuilder.of(issuerUri)
            .path(".well-known/openid-configuration")
            .build()
        val httpRequest = HttpRequest.GET<String>(discoveryUri)
            .accept(APPLICATION_JSON)

        return try {
            httpClient.retrieve(httpRequest, OidcDiscoveryResponse::class.java)
                .awaitFirst()
        } catch (e: Exception) {
            throw LocalizedException(
                detailsId = "config.provider.oidc.discovery_failed",
                values = mapOf("issuer" to issuerUri.toString()),
                throwable = e
            )
        }
    }
}
