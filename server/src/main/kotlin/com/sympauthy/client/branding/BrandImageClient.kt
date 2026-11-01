package com.sympauthy.client.branding

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.ContentLengthExceededException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirst
import java.net.URI

@Singleton
class BrandImageClient(
    @Inject private val httpClient: HttpClient
) {

    /**
     * What the host at [uri] answers, or null where it answers nothing this server would hold: an empty body, or
     * one over what its HTTP client buffers, which is an answer however large, and not the outage its exception
     * would otherwise be read as.
     */
    suspend fun fetchImage(uri: URI): ByteArray? {
        val request = HttpRequest.GET<Any>(uri).accept("image/svg+xml", "image/png")
        return try {
            httpClient.exchange(request, ByteArray::class.java).awaitFirst().body()
        } catch (_: ContentLengthExceededException) {
            null
        }
    }
}
