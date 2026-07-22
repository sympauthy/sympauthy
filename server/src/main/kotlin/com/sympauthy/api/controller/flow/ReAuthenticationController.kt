package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.resource.flow.ReAuthenticationContextResource
import com.sympauthy.api.resource.flow.ReAuthenticationProviderResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.flow.reauth.WebAuthorizationFlowProviderAttachManager
import com.sympauthy.business.model.reauth.ReAuthenticationPurpose
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

/**
 * Endpoints supporting the re-authentication (forced re-login) step. The re-login itself reuses the existing
 * sign-in endpoints (password via `POST /api/v1/flow/sign-in`, linked provider via the provider endpoints); this
 * controller only exposes the per-attempt context needed to render the banner and the action to decline.
 */
@Secured(HAS_STATE)
@Controller("/api/v1/flow/reauth")
class ReAuthenticationController(
    @Inject private val providerAttachManager: WebAuthorizationFlowProviderAttachManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil
) {

    @Operation(
        description = """
Return the context needed to render the re-authentication banner on the sign-in page: which existing account is
being confirmed, which provider is being attached, and which credentials the account can re-authenticate with.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getContext(
        authentication: Authentication
    ): ReAuthenticationContextResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenRun(
            state = authentication.stateOrNull,
            run = { authorizeAttempt, _ ->
                val context = providerAttachManager.getAttachContext(authorizeAttempt)
                ReAuthenticationContextResource(
                    purpose = ReAuthenticationPurpose.PROVIDER_ATTACH.name,
                    provider = ReAuthenticationProviderResource(
                        id = context.providerId,
                        name = context.providerName
                    ),
                    identifiers = context.targetIdentifierClaims
                        .mapNotNull { claim -> claim.value?.let { claim.claim.id to it.toString() } }
                        .toMap(),
                    availableMethods = context.availableMethods.map { it.name }
                )
            }
        )

    @Operation(
        description = """
Decline the pending re-authentication (e.g. the end-user does not want to attach the provider to their existing
account). Where sign-up is allowed, a separate account is created for the provider identity instead; otherwise the
sign-in is rejected. Then redirect the end-user to the next step of the flow.
        """,
        responses = [
            ApiResponse(
                description = "The re-authentication has been declined. The end-user may be redirected.",
                responseCode = "200"
            )
        ],
        tags = ["flow"]
    )
    @Post("/decline")
    suspend fun decline(
        authentication: Authentication
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { authorizeAttempt, _ ->
                providerAttachManager.decline(authorizeAttempt)
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
