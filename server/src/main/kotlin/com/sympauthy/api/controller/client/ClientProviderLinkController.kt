package com.sympauthy.api.controller.client

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.resource.client.ClientProviderLinkInputResource
import com.sympauthy.api.resource.client.ClientProviderLinkResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.security.SecurityRule.CLIENT_USERS_PROVIDERS_WRITE
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject

/**
 * Client-API entry point for a client to start linking a third-party identity provider to one of its
 * signed-in end-users, outside of an OAuth2 authorization (e.g. from a "manage sign-in methods" screen).
 *
 * Dual authentication:
 * - the **calling client** authenticates with a client-credentials token holding the `users:providers:write`
 *   scope.
 * - the **end-user** is identified by their access token, passed in the request body and validated to have
 *   been issued to the calling client.
 *
 * It starts an [com.sympauthy.business.model.flow.InteractiveFlowPurpose.LINK_PROVIDER] session for that
 * end-user (gated by a confirmation and — because linking a provider mints a durable login credential — a
 * forced re-authentication) and hands the caller the initial signed `state` + the page URL to drive the
 * browser to. Once the link completes, the end-user is redirected back to the caller-provided `return_uri`.
 */
@Secured(CLIENT_USERS_PROVIDERS_WRITE)
@Controller(ClientProviderLinkController.CLIENT_PROVIDER_LINK_ENDPOINT)
class ClientProviderLinkController(
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val clientRedirectUriManager: ClientRedirectUriManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val linkProviderManager: InteractiveFlowSessionLinkProviderManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
) {

    @Operation(
        description = """
Starts linking an identity provider to one of the calling client's signed-in end-users.

Validates the end-user `access_token` (must be a valid, unexpired user access token issued to the calling
client), the `{providerId}` (must be a known, enabled provider) and the `return_uri` (must be one of the
calling client's registered redirect URIs), creates a link session and returns the signed `state` and the
`redirect_url` the caller must navigate the end-user's browser to. The end-user is asked to confirm and to
re-authenticate before the link is created; once it completes they are redirected to `return_uri`.
        """,
        tags = ["client"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The link session was started.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid access token, or invalid return URI."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid client access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: users:providers:write."
            ),
            ApiResponse(responseCode = "404", description = "No enabled provider found with the given identifier.")
        ]
    )
    @Post
    @Secured(CLIENT_USERS_PROVIDERS_WRITE)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.USERS_PROVIDERS_WRITE])
    suspend fun startLink(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Identifier of the provider to link.") providerId: String,
        @Body resource: ClientProviderLinkInputResource
    ): ClientProviderLinkResource {
        val client = clientManager.findClientById(authentication.clientAuthentication.clientId)

        // Validate the end-user access token: signature, expiry, not revoked, issued to the calling client
        // (enforced by introspectToken), and associated with an end-user (not a client-credentials token).
        val userToken = resource.accessToken
            ?.let { tokenManager.introspectToken(client, it, "access_token") }
        val userId = userToken?.userId
            ?: throw recoverableBusinessExceptionOf(
                "client.providers.link.invalid_access_token",
                "description.client.providers.link.invalid_access_token"
            )

        // Fail fast (before creating any session) on an unknown or disabled provider. Like the unknown-user /
        // unknown-client cases, an absent named resource is a 404 (the provider id is a path variable).
        providerManager.listEnabledProviders().find { it.id == providerId }.orNotFound()

        // Validate the return URI (and the optional cancel URI) against the calling client's registered
        // redirect URIs to avoid open redirects. recoverable = true: a bad URI is a bad request from the
        // calling client (400), not a server error.
        val returnUri = clientRedirectUriManager.parseRequestedRedirectUri(client, resource.returnUri, recoverable = true)
        val cancelUri = resource.cancelUri
            ?.let { clientRedirectUriManager.parseRequestedRedirectUri(client, it, recoverable = true) }

        val flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow()
        val session = linkProviderManager.startLinkProviderSession(
            userId = userId,
            providerId = providerId,
            returnUri = returnUri,
            flow = flow,
            initiatingClientId = client.id,
            cancelUri = cancelUri
        )

        val (steppedSession, step) = engine.advance(session)
        val redirectUri = stepUriMapper.toRedirectUri(steppedSession, flow, step)
        return ClientProviderLinkResource(
            state = sessionManager.encodeState(steppedSession),
            redirectUrl = redirectUri.toString()
        )
    }

    companion object {
        const val CLIENT_PROVIDER_LINK_ENDPOINT = "/api/v1/client/providers/{providerId}/link"
    }
}
