package com.sympauthy.client.openidconnect

import com.sympauthy.client.openidconnect.model.OpenIdConnectDiscoveryResponse
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
class OpenIdConnectDiscoveryClient(
    @Inject private val httpClient: HttpClient
) {

    suspend fun fetchDiscovery(issuerUri: URI): OpenIdConnectDiscoveryResponse {
        val discoveryUri = UriBuilder.of(issuerUri)
            .path(".well-known/openid-configuration")
            .build()
        val httpRequest = HttpRequest.GET<String>(discoveryUri)
            .accept(APPLICATION_JSON)

        return try {
            httpClient.retrieve(httpRequest, OpenIdConnectDiscoveryResponse::class.java)
                .awaitFirst()
        } catch (e: Exception) {
            throw LocalizedException(
                detailsId = "provider.openid_connect.discovery_failed",
                values = mapOf("issuer" to issuerUri.toString()),
                throwable = e
            )
        }
    }
}