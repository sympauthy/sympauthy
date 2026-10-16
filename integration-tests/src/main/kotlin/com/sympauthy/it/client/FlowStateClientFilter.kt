package com.sympauthy.it.client

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Singleton

/**
 * Holds the signed internal state to attach to the generated client's requests against the flow surface for
 * the current scenario.
 *
 * The state is read off the `/authorize` redirect — after the [io.micronaut.context.ApplicationContext] that
 * hosts the generated client is created — so it is set on this mutable holder rather than baked into static
 * client configuration, as [BearerTokenHolder] does for a token.
 */
@Singleton
class FlowStateHolder {
    @Volatile
    var state: String? = null
}

/**
 * Attaches the state from [FlowStateHolder] to every flow-surface request the generated `@Client("sympauthy")`
 * makes, where the server reads it: an `Authorization: State <state>` header on a `POST`, and the `state`
 * query parameter on any other method.
 *
 * The contract names the state as no operation's parameter, so the generated client exposes none; it is
 * injected here instead of being passed at each call site, as [BearerTokenClientFilter] does for a token.
 */
@ClientFilter(serviceId = ["sympauthy"])
class FlowStateClientFilter(private val holder: FlowStateHolder) {

    @RequestFilter
    fun addState(request: MutableHttpRequest<*>) {
        val state = holder.state ?: return
        if (!request.path.startsWith("/api/v1/flow/")) return
        if (request.method == HttpMethod.POST) {
            // Set rather than added, so the state replaces whatever a filter that ran before this one put there.
            request.headers.set(HttpHeaders.AUTHORIZATION, "State $state")
        } else if (!request.parameters.contains("state")) {
            request.uri(UriBuilder.of(request.uri).queryParam("state", state).build())
        }
    }
}
