package com.sympauthy.it.client

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.json.JsonMapper
import jakarta.inject.Singleton
import java.net.URI

/**
 * A non-redirect-following Micronaut HTTP client for the flow endpoints the generated typed client
 * cannot express directly:
 * - the `/authorize` GET, whose `303` `Location` carries the signed internal state as `?state=<jwt>`;
 * - the state-secured `POST /api/v1/flow/cancel`, whose `Authorization: State <jwt>` is not a generated
 *   parameter (state is a security scheme, not an operation argument).
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
    private val jsonMapper: JsonMapper,
) {

    /** GETs [url] (absolute or relative) without following redirects. */
    fun get(url: String): FlowCall = exchange(HttpRequest.GET<Any>(relativize(url)))

    /** POSTs the bodyless `/api/v1/flow/cancel`, authenticated by the signed [state]. */
    fun cancel(state: String): FlowCall = exchange(
        HttpRequest.create<Any>(HttpMethod.POST, "/api/v1/flow/cancel")
            .header("Authorization", "State $state"),
    )

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
        return FlowCall(status, location, body, parseFields(body))
    }

    /** The top-level scalar JSON fields of [body] (e.g. `redirect_url`, `state`); empty if not a JSON object. */
    private fun parseFields(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        return try {
            val map = jsonMapper.readValue(body, Argument.mapOf(String::class.java, Any::class.java))
                ?: return emptyMap()
            map.entries
                .filter { (_, value) -> value !is Map<*, *> && value !is Collection<*> }
                .associate { (key, value) -> key to value.toString() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** A relative path (+query) for a same-host URL, so the service-bound client resolves it against its base. */
    private fun relativize(url: String): String {
        val uri = URI.create(url)
        if (uri.host == null) return url
        return if (uri.rawQuery != null) "${uri.rawPath}?${uri.rawQuery}" else uri.rawPath
    }
}

/**
 * The outcome of a flow HTTP call: the response [status], any `Location` header, the raw [body], and its
 * top-level string JSON [fields] (e.g. `redirect_url`, `state`) parsed for convenience.
 */
data class FlowCall(
    val status: Int,
    val location: String?,
    val body: String,
    val fields: Map<String, String>,
)
