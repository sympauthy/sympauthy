package com.sympauthy.api.controller.flow

import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.resource.flow.FlowResultResource
import com.sympauthy.api.resource.flow.SignUpInputResource
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowPasswordManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.security.SecurityRule.HAS_STATE
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("/api/v1/flow/sign-up")
class SignUpController(
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val passwordFlowManager: WebAuthorizationFlowPasswordManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper
) {

    @Operation(
        description = """
Initiate the creation of an account of a end-user with a password.
        """,
        tags = ["flow"]
    )
    @Post
    suspend fun signUp(
        authentication: Authentication,
        @Body inputResource: SignUpInputResource
    ): FlowResultResource =
        webAuthorizationFlowManager.extractFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, flow ->
            val updates = collectedClaimUpdateMapper.toUpdates(inputResource.claims)
            val result = passwordFlowManager.signUpWithClaimsAndPassword(
                authorizeAttempt = authorizeAttempt,
                unfilteredUpdates = updates,
                password = inputResource.password
            )
            redirectUriBuilder.getRedirectUri(
                authorizeAttempt = authorizeAttempt,
                flow = flow,
                status = result
            ).toString().let(::FlowResultResource)
        }
}
