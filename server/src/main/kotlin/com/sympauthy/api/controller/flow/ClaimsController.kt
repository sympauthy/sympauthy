package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.flow.ClaimsResourceMapper
import com.sympauthy.api.resource.flow.ClaimInputResource
import com.sympauthy.api.resource.flow.ClaimsFlowResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.flow.WebAuthorizationFlowPasswordManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
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
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val passwordFlowManager: WebAuthorizationFlowPasswordManager,
    @Inject private val claimsMapper: ClaimsResourceMapper,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil
) {

    @Operation(
        description = """
List all claims already collected by this authorization server for the end-user signing-in/up through the authorization flow.

It includes claims:
- collected as a first-party. ex. a new required claim has been added forcing the end-user to go through the claim 
collection step of the authorization flow again.
- collected from a provider used by the end-user. In this case, the value may be suggested to the user 
(by auto-filling the input) and the user is free to enter another value before confirming.

If there is no claim to collect from the end-user,  the response will contain the redirect URL where the user 
must be redirected to continue the authorization flow.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getCollectedClaims(
        authentication: Authentication,
    ): ClaimsFlowResource = webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenRunAndRedirect(
        state = authentication.stateOrNull,
        run = { authorizeAttempt, _ ->
            collectedClaimManager.findByAttempt(authorizeAttempt).ifEmpty { null }
        },
        mapResultToResource = { claimsMapper.toResource(it) },
        mapRedirectUriToResource = { claimsMapper.toResource(it) },
    )

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
                val signUpClaims = passwordFlowManager.getSignUpClaims()
                collectedClaimManager.update(
                    user = user,
                    updates = collectedClaimUpdateMapper.toUpdates(inputResource.claims)
                        .filter { it.claim.userInputted }
                        .filter { !signUpClaims.contains(it.claim) }
                )
                authorizeAttempt
            },
            mapRedirectUriToResource = { SimpleFlowResource(it.toString()) }
        )
}
