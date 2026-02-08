package com.sympauthy.api.controller.flow

import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.flow.ClaimsResourceMapper
import com.sympauthy.api.resource.flow.ClaimInputResource
import com.sympauthy.api.resource.flow.ClaimsFlowResource
import com.sympauthy.api.resource.flow.FlowResultResource
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowPasswordManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.security.SecurityRule.HAS_STATE
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
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val passwordFlowManager: WebAuthorizationFlowPasswordManager,
    @Inject private val claimsMapper: ClaimsResourceMapper,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder
) {

    @Operation(
        description = """
List all claims already collected by this authorization server for the end-user signing-in/up through the authorization flow.

It includes claims:
- collected as a first-party. ex. a new required claim has been added forcing the end-user to go through the claim 
collection step of the authorization flow again.
- collected from a provider used by the end-user. In this case, the value may be suggested to the user 
(by auto-filling the input) and the user is free to enter another value before confirming. 
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getCollectedClaims(
        authentication: Authentication,
    ): ClaimsFlowResource = webAuthorizationFlowManager.extractUserFromAuthenticationAndVerifyThenRun(
        authentication = authentication
    ) { authorizeAttempt, flow, user ->
        if (user != null) {
            val collectedClaims = collectedClaimManager.findReadableUserInfoByUserId(user.id)
            claimsMapper.toResource(collectedClaims = collectedClaims)
        } else {
            val status = webAuthorizationFlowManager.getStatusAndCompleteIfNecessary(
                authorizeAttempt = authorizeAttempt
            )
            val redirectUri = redirectUriBuilder.getRedirectUri(
                authorizeAttempt = authorizeAttempt,
                flow = flow,
                status = status
            )
            claimsMapper.toResource(redirectUri = redirectUri)
        }
    }

    @Operation(
        description = """
Save claims collected from the end-user signing-in/up through the authorization flow.

A claim will be saved if:
- it is collectable.
- it is not part of the sign-up claims.

Null/Empty value can be associated to claim to notify the authentication server that the claim has been presented to
the end-user but it declined to fulfill the value.
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
    ): FlowResultResource =
        webAuthorizationFlowManager.extractUserFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, flow, user ->
            if (user != null) {
                val updates = getUpdates(inputResource)
                collectedClaimManager.update(user, updates = updates)
            }

            val status = webAuthorizationFlowManager.getStatusAndCompleteIfNecessary(
                authorizeAttempt = authorizeAttempt
            )
            val redirectUri = redirectUriBuilder.getRedirectUri(
                authorizeAttempt = authorizeAttempt,
                flow = flow,
                status = status
            )
            FlowResultResource(redirectUri.toString())
        }

    private fun getUpdates(inputResource: ClaimInputResource): List<CollectedClaimUpdate> {
        val signUpClaims = passwordFlowManager.getSignUpClaims()
        return collectedClaimUpdateMapper.toUpdates(inputResource.claims)
            .filter { it.claim.userInputted }
            .filter { !signUpClaims.contains(it.claim) }
    }
}
