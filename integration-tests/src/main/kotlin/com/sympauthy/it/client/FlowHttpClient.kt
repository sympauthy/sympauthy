package com.sympauthy.it.client

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import java.net.URI

/**
 * A non-redirect-following Micronaut HTTP client for the one flow request a scenario makes outside the
 * generated typed client: the `/authorize` GET, whose `303` `Location` carries the signed internal state
 * as `?state=<jwt>` — a redirect the scenario reads rather than follows.
 *
 * It returns a plain [FlowCall] (status + `Location` + body) rather than throwing on non-2xx, so a
 * scenario can assert on a `303` redirect or a recoverable `4xx` the same way.
 *
 * Bound to the `sympauthy` service ([Client]); the hosting context must set
 * `micronaut.http.services.sympauthy.follow-redirects=false` so the `/authorize` `303` is observable.
 */
@Singleton
class FlowHttpClient(
    @param:Client("sympauthy") private val http: HttpClient,
) {

    /** GETs [url] (absolute or relative) without following redirects. */
    fun get(url: String): FlowCall = exchange(HttpRequest.GET<Any>(relativize(url)))

    private fun exchange(request: HttpRequest<*>): FlowCall {
        val (status, location, body) = try {
            val response = http.toBlocking().exchange(request, String::class.java)
            Triple(response.status.code, response.header("Location"), response.body() ?: "")
        } catch (e: HttpClientResponseException) {
            val response = e.response
            Triple(
                response.status.code,
                response.headers.get("Location"),
                response.getBody(String::class.java).orElse(""),
            )
        }
        return FlowCall(status, location, body)
    }

    /** A relative path (+query) for a same-host URL, so the service-bound client resolves it against its base. */
    private fun relativize(url: String): String {
        val uri = URI.create(url)
        if (uri.host == null) return url
        return if (uri.rawQuery != null) "${uri.rawPath}?${uri.rawQuery}" else uri.rawPath
    }
}

/** The outcome of a flow HTTP call: the response [status], any `Location` header, and the raw [body]. */
data class FlowCall(
    val status: Int,
    val location: String?,
    val body: String,
)
