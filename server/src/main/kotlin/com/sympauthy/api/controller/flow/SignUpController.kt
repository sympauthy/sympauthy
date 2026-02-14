package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.resource.flow.SignUpInputResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.flow.WebAuthorizationFlowPasswordManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
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
    @Inject private val passwordFlowManager: WebAuthorizationFlowPasswordManager,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil
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
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { authorizeAttempt, _ ->
                val updates = collectedClaimUpdateMapper.toUpdates(inputResource.claims)
                passwordFlowManager.signUpWithClaimsAndPassword(
                    authorizeAttempt = authorizeAttempt,
                    unfilteredUpdates = updates,
                    password = inputResource.password
                )
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
