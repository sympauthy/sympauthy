package com.sympauthy.security

import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.filters.AuthenticationFetcher
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

/**
 * Handles the authentication logic for flow APIs.
 *
 * The authentication fetcher retrieves the state parameter from the request and carries it on the
 * [Authentication] it returns, where [stateOrNull] reads it.
 *
 * It answers on the thread it is called on, without a coroutine: it awaits nothing, and the fetcher after it is then
 * built where the request's context is still bound, which is what [AccessTokenValidator] reads its trace from.
 */
@Singleton
class StateAuthenticationFetcher : AuthenticationFetcher<HttpRequest<*>> {

    override fun fetchAuthentication(request: HttpRequest<*>): Publisher<Authentication> {
        if (!request.path.contains("/flow/")) {
            return Publishers.empty()
        }

        val state = if (request.method == HttpMethod.POST) {
            request.headers.authorization
                .orElse(null)
                ?.takeIf { it.startsWith("State ") }
                ?.removePrefix("State ")
                ?.takeIf { it.isNotBlank() }
        } else {
            request.parameters["state"]?.takeIf { it.isNotBlank() }
        }
        return state?.let { Publishers.just(StateAuthentication(it)) } ?: Publishers.empty()
    }
}
