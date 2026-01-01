package com.sympauthy.api.controller.flow

import com.sympauthy.api.resource.flow.FlowResultResource
import com.sympauthy.api.resource.flow.SignInInputResource
import com.sympauthy.business.manager.flow.PasswordFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.security.SecurityRule.HAS_STATE
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Controller("/api/v1/flow/sign-in")
@Secured(HAS_STATE)
class SignInController(
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val passwordFlowManager: PasswordFlowManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder,
) {

    @Operation(
        description = "Sign-in using a login and a password.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The credentials provided where valid. The authentication flow will continue.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun signIn(
        authentication: Authentication,
        @Body inputResource: SignInInputResource
    ): FlowResultResource =
        webAuthorizationFlowManager.extractFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, flow ->
            val result = passwordFlowManager.signInWithPassword(
                authorizeAttempt = authorizeAttempt,
                login = inputResource.login,
                password = inputResource.password
            )
            redirectUriBuilder.getRedirectUri(
                authorizeAttempt = authorizeAttempt,
                flow = flow,
                result = result
            ).toString().let(::FlowResultResource)
        }
}
