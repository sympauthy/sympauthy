package com.sympauthy.security

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.filters.AuthenticationFetcher
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.publish
import org.reactivestreams.Publisher

/**
 * Handles the authentication logic for flow APIs.
 *
 * The authentication fetcher retrieves the state parameter from the request query parameters
 * and put it in the [Authentication] for uses by the [com.sympauthy.business.manager.flow.WebAuthorizationFlowManager].
 */
@Singleton
class StateAuthenticationFetcher : AuthenticationFetcher<HttpRequest<*>> {

    override fun fetchAuthentication(request: HttpRequest<*>): Publisher<Authentication> {
        return publish {
            if (!request.path.contains("/flow/")) {
                return@publish
            }

            val state = if (request.method == HttpMethod.POST) {
                request.headers.authorization
                    ?.orElse(null)
                    ?.takeIf { it.startsWith("State ") }
                    ?.removePrefix("State ")
                    ?.takeIf { it.isNotBlank() }
            } else {
                request.parameters["state"]?.takeIf { it.isNotBlank() }
            }
            this.send(StateAuthentication(state))
        }
    }
}
