package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.flow.ClaimsResourceMapper
import com.sympauthy.api.resource.flow.ClaimInputResource
import com.sympauthy.api.resource.flow.ClaimsFlowResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.user.ConsentAwareClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("/api/v1/flow/claims")
class ClaimsController(
    @Inject private val consentAwareClaimManager: ConsentAwareClaimManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val claimsMapper: ClaimsResourceMapper,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil,
) {

    @Operation(
        description = """
List all collectable claims for the end-user signing-in/up through the authorization flow,
along with their metadata (name, type, required, group), any already-collected values,
and suggested values from external providers.

Identifier claims are excluded as they require separate validation.
Only claims within the end-user's consented scopes are returned.

If there are no collectable claims, the response will contain the redirect URL where the user
must be redirected to continue the authorization flow.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getCollectableClaims(
        authentication: Authentication,
        httpRequest: HttpRequest<*>
    ): ClaimsFlowResource {
        val locale = httpRequest.locale.orDefault()
        return webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenRunAndRedirect(
            state = authentication.stateOrNull,
            run = { authorizeAttempt, _ ->
                val collectableClaims = consentAwareClaimManager.listCollectableClaimsByAttempt(authorizeAttempt)
                if (collectableClaims.isEmpty()) {
                    null
                } else {
                    val collectedClaims = consentAwareCollectedClaimManager.findByAttempt(authorizeAttempt)
                    val providerUserInfoList = authorizeAttempt.userId?.let {
                        providerClaimsManager.findByUserId(it)
                    } ?: emptyList()
                    Triple(collectableClaims, collectedClaims, providerUserInfoList)
                }
            },
            mapResultToResource = { (collectableClaims, collectedClaims, providerUserInfoList) ->
                claimsMapper.toResource(collectableClaims, collectedClaims, providerUserInfoList, locale)
            },
            mapRedirectUriToResource = { claimsMapper.toResource(it) },
        )
    }

    @Operation(
        description = """
Save claims collected from the end-user signing-in/up through the authorization flow then redirect the end-user to the
next step of the flow.

A claim will be saved if:
- it is collectable.
- it is not part of the sign-up claims.

A null or empty value can be assigned to a claim to indicate that the claim was presented to the end-user,
but they chose not to provide a value.
        """,
        responses = [
            ApiResponse(
                description = "Claims have been collected. The end-user may be redirected to the next step of the flow.",
                responseCode = "200"
            )
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun collectClaims(
        authentication: Authentication,
        @Body inputResource: ClaimInputResource
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptWithUserThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { authorizeAttempt, _, user ->
                consentAwareCollectedClaimManager.updateByUser(
                    user = user,
                    updates = collectedClaimUpdateMapper.toUpdates(inputResource.claims),
                    consentedScopes = authorizeAttempt.consentedScopes ?: emptyList()
                )
                authorizeAttempt
            },
            mapRedirectUriToResource = { SimpleFlowResource(it.toString()) }
        )
}
