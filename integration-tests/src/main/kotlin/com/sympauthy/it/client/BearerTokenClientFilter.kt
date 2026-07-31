package com.sympauthy.it.client

import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.RequestFilter
import jakarta.inject.Singleton

/**
 * Holds the bearer token to attach to generated-client requests for the current scenario.
 *
 * The token is obtained during the interactive flow — after the [io.micronaut.context.ApplicationContext]
 * that hosts the generated client is created — so it is set on this mutable holder just before an
 * authenticated call rather than baked into static client configuration.
 */
@Singleton
class BearerTokenHolder {
    @Volatile
    var token: String? = null
}

/**
 * Attaches `Authorization: Bearer <token>` to every request the generated `@Client("sympauthy")` makes,
 * reading the current token from [BearerTokenHolder].
 *
 * The generated client exposes no auth parameter — OAuth2 is declared in the contract via securitySchemes,
 * not per-operation — so the header is injected here instead of being passed at each call site.
 */
@ClientFilter(serviceId = ["sympauthy"])
class BearerTokenClientFilter(private val holder: BearerTokenHolder) {

    @RequestFilter
    fun addBearerToken(request: MutableHttpRequest<*>) {
        holder.token?.let(request::bearerAuth)
    }
}
