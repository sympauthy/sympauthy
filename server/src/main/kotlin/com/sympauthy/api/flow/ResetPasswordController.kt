package com.sympauthy.api.flow

import com.sympauthy.api.FLOW_SURFACE
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.flow.mapper.ClientReferenceResourceMapper
import com.sympauthy.api.flow.mapper.CollectableClaimResourceMapper
import com.sympauthy.api.flow.resource.ResendResetPasswordCodeResultResource
import com.sympauthy.api.flow.resource.ResetPasswordChoiceFlowResource
import com.sympauthy.api.flow.resource.ResetPasswordChoiceInputResource
import com.sympauthy.api.flow.resource.ResetPasswordFlowResource
import com.sympauthy.api.flow.resource.ResetPasswordInputResource
import com.sympauthy.api.flow.resource.ResetPasswordValidationFlowResource
import com.sympauthy.api.flow.resource.ResetPasswordValidationInputResource
import com.sympauthy.api.flow.resource.SimpleFlowResource
import com.sympauthy.api.flow.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_ONGOING_SESSION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.mapper.InteractiveFlowStepUriMapper
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.manager.claim.ClaimManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionPasswordResetManager
import com.sympauthy.business.manager.flow.reset.InteractiveFlowSessionPasswordResetManager
import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.platform.util.orDefault
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

/**
 * The steps an end-user resets a forgotten password through, reached from the sign-in step: asking for a code for a
 * login, entering the code, and choosing the new password.
 */
@Controller("$FLOW_SURFACE/password-reset")
@Secured(HAS_STATE)
class ResetPasswordController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val passwordResetFlowManager: InteractiveAuthFlowSessionPasswordResetManager,
    @Inject private val passwordResetManager: InteractiveFlowSessionPasswordResetManager,
    @Inject private val collectableClaimResourceMapper: CollectableClaimResourceMapper,
    @Inject private val clientMapper: ClientReferenceResourceMapper,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = "Retrieve the step where the end-user asks to reset a forgotten password: the identifier " +
            "claims whose value is a login. Once the end-user is signed in, the response carries only " +
            "`redirect_url`. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The configuration of the step, or the `redirect_url` to send the browser to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getResetPasswordConfiguration(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        httpRequest: HttpRequest<*>
    ): ResetPasswordFlowResource {
        val locale = httpRequest.locale.orDefault()
        val state = authentication.stateOrNull
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = state,
            observedRequest = observedRequest,
            run = { session, flow ->
                passwordResetFlowManager.checkOffered(flow)
                // Mirrors the sign-in step: asking for a code applies while nobody is signed in.
                if (session.userId == null) {
                    ResetPasswordFlowResource(
                        identifierClaims = collectableClaimResourceMapper.toResources(
                            claimManager.listIdentifierClaims(), locale
                        ),
                        client = clientMapper.toResourceOrNull(session),
                        cancelUrl = stepUriMapper.getCancelUriOrNull(session, state)?.toString()
                    )
                } else null
            },
            mapResultToResource = { it },
            mapRedirectUriToResource = { ResetPasswordFlowResource(redirectUrl = it.toString()) }
        )
    }

    @Operation(
        description = "Ask to reset the password of the user holding a login. The end-user is sent to the step " +
            "entering a code whether or not a user holds the login: a code is sent only to the verified email " +
            "address of an enabled user holding a password. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = FLOW_REDIRECT_RESPONSE_DESCRIPTION,
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.password.reset.missing_login`: `login` is absent or blank.\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun requestCode(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: ResetPasswordInputResource
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, flow -> passwordResetFlowManager.requestCode(session, flow, inputResource.login) },
            mapRedirectUriToResource = { SimpleFlowResource(it.toString()) }
        )

    @Operation(
        description = "Retrieve the step where the end-user enters the code sent to reset their password. Once the " +
            "code is entered, or when no reset was asked for, the response carries only `redirect_url`. " +
            FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The configuration of the step, or the `redirect_url` to send the browser to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get("/validation")
    suspend fun getValidationConfiguration(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): ResetPasswordValidationFlowResource {
        val state = authentication.stateOrNull
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = state,
            observedRequest = observedRequest,
            run = { session, flow ->
                passwordResetFlowManager.checkOffered(flow)
                // Mirrors PasswordResetInteractiveFlowPurposeHandler: the code is entered until it proves the user.
                passwordResetManager.fetchPasswordResetOrNull(session)
                    ?.takeIf { it.codeProvenDate == null && session.userId == null }
                    ?.let {
                        ResetPasswordValidationFlowResource(
                            login = it.login,
                            resendDate = passwordResetFlowManager.getResendDate(it),
                            client = clientMapper.toResourceOrNull(session),
                            cancelUrl = stepUriMapper.getCancelUriOrNull(session, state)?.toString()
                        )
                    }
            },
            mapResultToResource = { it },
            mapRedirectUriToResource = { ResetPasswordValidationFlowResource(redirectUrl = it.toString()) }
        )
    }

    @Operation(
        description = "Prove the user whose password the end-user is resetting, with the code they entered. " +
            FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = FLOW_REDIRECT_RESPONSE_DESCRIPTION,
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.password.reset.invalid_code`: `code` is absent, matches no code sent during this " +
                    "interactive flow session, or matches one that has expired. A code entered where no user holds " +
                    "the login is refused the same way.\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post("/validation")
    suspend fun validateCode(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: ResetPasswordValidationInputResource
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, flow ->
                passwordResetFlowManager.validateCode(session, flow, inputResource.code, observedRequest)
            },
            mapRedirectUriToResource = { SimpleFlowResource(it.toString()) }
        )

    @Operation(
        description = "Ask for a new code to reset a password, replacing the one sent earlier in this interactive " +
            "flow session. Nothing is asked for, and `resent` is `false`, before `resend_date`, once the code " +
            "has been entered, or when no reset was asked for. The answer is the same whether or not a user " +
            "holds the login.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Whether a new code was asked for, and when another can be.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION + FLOW_ONGOING_SESSION_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post("/validation/resend")
    suspend fun resendCode(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): ResendResetPasswordCodeResultResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRun(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, flow ->
                val result = passwordResetFlowManager.resendCode(session, flow)
                ResendResetPasswordCodeResultResource(
                    resent = result.resent,
                    resendDate = result.passwordReset?.let(passwordResetFlowManager::getResendDate)
                )
            }
        )

    @Operation(
        description = "Retrieve the step where the end-user chooses the new password of the user a code proved. " +
            "Before the code is entered, or once a password is chosen, the response carries only `redirect_url`. " +
            FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The configuration of the step, or the `redirect_url` to send the browser to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get("/password")
    suspend fun getChoiceConfiguration(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): ResetPasswordChoiceFlowResource {
        val state = authentication.stateOrNull
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = state,
            observedRequest = observedRequest,
            run = { session, flow ->
                passwordResetFlowManager.checkOffered(flow)
                // Mirrors PasswordResetInteractiveFlowPurposeHandler: chosen once, for the user the code proved.
                passwordResetManager.fetchPasswordResetOrNull(session)
                    ?.takeIf {
                        it.codeProvenDate != null && it.userId == session.userId && it.newPassword == null
                    }
                    ?.let {
                        ResetPasswordChoiceFlowResource(
                            login = it.login,
                            client = clientMapper.toResourceOrNull(session),
                            cancelUrl = stepUriMapper.getCancelUriOrNull(session, state)?.toString()
                        )
                    }
            },
            mapResultToResource = { it },
            mapRedirectUriToResource = { ResetPasswordChoiceFlowResource(redirectUrl = it.toString()) }
        )
    }

    @Operation(
        description = "Choose the new password of the user a code proved. It replaces every other password of the " +
            "user, and the grants they were issued under are revoked, once the interactive flow session " +
            "completes. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = FLOW_REDIRECT_RESPONSE_DESCRIPTION,
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.password.reset.missing_password`: `password` is absent or blank.\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post("/password")
    suspend fun choosePassword(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: ResetPasswordChoiceInputResource
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, flow ->
                passwordResetFlowManager.choosePassword(session, flow, inputResource.password)
            },
            mapRedirectUriToResource = { SimpleFlowResource(it.toString()) }
        )
}
