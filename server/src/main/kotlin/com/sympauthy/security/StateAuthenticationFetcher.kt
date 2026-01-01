package com.sympauthy.security

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

            val state = request.parameters["state"]?.let {
                if (it.isNotBlank()) it else null
            }
            this.send(StateAuthentication(state))
        }
    }
}
