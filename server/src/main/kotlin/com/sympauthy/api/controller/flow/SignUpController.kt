package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.resource.flow.CollectableClaimResource
import com.sympauthy.api.resource.flow.PasswordResource
import com.sympauthy.api.resource.flow.SignUpFlowResource
import com.sympauthy.api.resource.flow.SignUpInputResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionPasswordManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionRedirectUriBuilder
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.server.DisplayMessages
import com.sympauthy.util.orDefault
import io.micronaut.context.MessageSource
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject
import java.util.Locale

@Secured(HAS_STATE)
@Controller("/api/v1/flow/sign-up")
class SignUpController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val passwordFlowManager: InteractiveAuthFlowSessionPasswordManager,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val redirectUriBuilder: InteractiveAuthFlowSessionRedirectUriBuilder,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
    @Inject @param:DisplayMessages private val displayMessageSource: MessageSource
) {

    @Operation(
        description = """
Return everything the custom UI needs to render the sign-up step, or a redirect URL if the end-user is not
expected to be on the sign-up step (ex. sign-up disabled for the audience redirects to sign-in, an
authenticated end-user is redirected to the next step).

This configuration only contains information associated to this authorization server, the client and the
on-going flow. All URLs it contains already include the state query param.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getSignUpConfiguration(
        authentication: Authentication,
        httpRequest: HttpRequest<*>
    ): SignUpFlowResource {
        val locale = httpRequest.locale.orDefault()
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
            run = { session, flow ->
                if (signUpApplies(session)) {
                    buildSignUpConfiguration(session, flow, locale)
                } else {
                    null
                }
            },
            mapResultToResource = { it },
            mapRedirectUriToResource = { SignUpFlowResource(redirectUrl = it.toString()) }
        )
    }

    /**
     * The sign-up step applies while no user is associated to the [session] yet and sign-up is allowed for
     * the audience of the client. When it does not apply, [InteractiveAuthFlowSessionRedirectUriBuilder] redirects an
     * unauthenticated end-user with no invitation to the sign-in page.
     */
    private suspend fun signUpApplies(
        session: OnGoingInteractiveFlowSession
    ): Boolean {
        if (session.userId != null) {
            return false
        }
        return interactiveAuthFlowSessionManager.isSignUpAllowed(session)
    }

    private suspend fun buildSignUpConfiguration(
        session: OnGoingInteractiveFlowSession,
        flow: InteractiveFlow,
        locale: Locale
    ): SignUpFlowResource {
        val password = if (passwordFlowManager.signUpEnabled) {
            PasswordResource(
                identifierClaims = claimManager.listIdentifierClaims().map { it.id }
            )
        } else null
        val claims = claimManager.listCollectableClaims().map { claim ->
            CollectableClaimResource(
                id = claim.id,
                required = claim.required,
                name = displayMessageSource.getMessage("claims.${claim.id}.name", claim.id, locale),
                group = claim.group?.name?.lowercase(),
                type = claim.dataType.name.lowercase()
            )
        }
        val signInRedirectUrl = if (oauth2Manager.fetchOAuth2(session).invitationId == null) {
            redirectUriBuilder.getSignInRedirectUri(session, flow).toString()
        } else null
        return SignUpFlowResource(
            password = password,
            claims = claims,
            signInRedirectUrl = signInRedirectUrl
        )
    }

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
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { session, _ ->
                val updates = collectedClaimUpdateMapper.toUpdates(inputResource.claims)
                passwordFlowManager.signUpWithClaimsAndPassword(
                    session = session,
                    unfilteredUpdates = updates,
                    password = inputResource.password
                )
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
