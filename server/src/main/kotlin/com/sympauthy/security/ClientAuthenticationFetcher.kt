package com.sympauthy.security

import com.sympauthy.business.manager.ClientManager
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.BasicAuthUtils
import io.micronaut.security.filters.AuthenticationFetcher
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.publish
import org.reactivestreams.Publisher

/**
 * [AuthenticationFetcher] that authenticates our OAuth2 [Client].
 * It the authentication succeed, it creates an [Authentication] with the role [SecurityRule.IS_CLIENT].
 *
 * It supports:
 * - Client id & client secret transmitted using Basic Auth.
 */
@Singleton
class ClientAuthenticationFetcher(
    @Inject private val clientManager: ClientManager
) : AuthenticationFetcher<HttpRequest<*>> {

    override fun fetchAuthentication(request: HttpRequest<*>): Publisher<Authentication> {
        return publish {
            getClientCredentialsFromRequest(request)
                ?.let { authenticateClient(it) }
                ?.let { send(it) }
        }
    }

    private fun getClientCredentialsFromRequest(request: HttpRequest<*>): ClientCredentials? {
        return sequenceOf(
            this::getClientCredentialsFromHeader,
            this::getClientCredentialsFromBody
        ).firstNotNullOfOrNull { it.invoke(request) }
    }

    private fun getClientCredentialsFromHeader(request: HttpRequest<*>): ClientCredentials? {
        val credentials = request.headers.authorization
            ?.flatMap(BasicAuthUtils::parseCredentials)
            ?.orElse(null)
            ?: return null
        return ClientCredentials(
            clientId = credentials.username,
            clientSecret = credentials.password
        )
    }

    private fun getClientCredentialsFromBody(request: HttpRequest<*>): ClientCredentials? {
        val body = request.body.orElse(null) as? Map<*, *> ?: return null
        val clientId = body["client_id"] as? String
        val clientSecret = body["client_secret"] as? String
        return if (clientId != null && clientSecret != null) {
            ClientCredentials(clientId, clientSecret)
        } else null
    }

    private suspend fun authenticateClient(credentials: ClientCredentials?): Authentication? {
        if (credentials == null) {
            return null
        }
        val client = clientManager.authenticateClient(
            clientId = credentials.clientId,
            clientSecret = credentials.clientSecret
        )
        return client?.let(::ClientAuthentication)
    }
}

private data class ClientCredentials(
    val clientId: String,
    val clientSecret: String
)
