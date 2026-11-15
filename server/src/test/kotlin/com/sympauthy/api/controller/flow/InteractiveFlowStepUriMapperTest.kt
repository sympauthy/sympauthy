package com.sympauthy.api.controller.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowHandBack
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.ACCESS_DENIED
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_SCOPE
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNAUTHORIZED_CLIENT
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowStepUriMapperTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var authorizationCodeManager: AuthorizationCodeManager

    @MockK
    lateinit var uncheckedUrlsConfig: UrlsConfig

    @SpyK
    @InjectMockKs
    lateinit var mapper: InteractiveFlowStepUriMapper

    private val authorizationCodeHandBack = InteractiveFlowHandBack(
        uri = URI.create("https://www.example.com"),
        type = InteractiveFlowRedirectType.AUTHORIZATION_CODE
    )

    private val plainHandBack = InteractiveFlowHandBack(
        uri = URI.create("https://client.example.com/account"),
        type = InteractiveFlowRedirectType.PLAIN,
        returnState = "xyz"
    )

    private val oauth2 = InteractiveFlowSessionOAuth2(
        sessionId = UUID.randomUUID(),
        clientId = "test-client",
        redirectUri = "https://www.example.com",
        requestedScopes = emptyList(),
        state = "clientState"
    )

    /**
     * A mapper reading the URLs of a deployment served at `https://auth.example.com`.
     */
    private fun urlsMapper() = InteractiveFlowStepUriMapper(
        sessionManager,
        oauth2Manager,
        authorizationCodeManager,
        EnabledUrlsConfig(root = URI.create("https://auth.example.com"))
    )

    @Test
    fun `toRedirectUri - CollectClaims maps to collectClaimsUri with state appended`() = runTest {
        val uri = URI.create("https://www.example.com/collect-claims")
        val session = mockk<OnGoingInteractiveFlowSession>()
        val flow = mockk<InteractiveFlow> { every { collectClaimsUri } returns uri }
        coEvery { mapper.appendState(session, uri) } returns uri

        val result = mapper.toRedirectUri(session, flow, InteractiveFlowStep.CollectClaims)

        assertEquals(uri, result)
    }

    @Test
    fun `toRedirectUri - Confirm maps to confirmUri with state appended`() = runTest {
        val uri = URI.create("https://www.example.com/confirm")
        val session = mockk<OnGoingInteractiveFlowSession>()
        val flow = mockk<InteractiveFlow> { every { confirmUri } returns uri }
        coEvery { mapper.appendState(session, uri) } returns uri

        val result = mapper.toRedirectUri(session, flow, InteractiveFlowStep.Confirm)

        assertEquals(uri, result)
    }

    @Test
    fun `toRedirectUri - Confirm throws when the flow has no confirm page`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val flow = mockk<InteractiveFlow> { every { confirmUri } returns null }

        val exception = assertThrows<BusinessException> {
            mapper.toRedirectUri(session, flow, InteractiveFlowStep.Confirm)
        }
        assertEquals("flow.confirm.uri.missing", exception.detailsId)
    }

    @Test
    fun `toRedirectUri - SignUp falls back to signInUri when the flow has no sign-up page`() = runTest {
        val signInUri = URI.create("https://www.example.com/sign-in")
        val session = mockk<OnGoingInteractiveFlowSession>()
        val flow = mockk<InteractiveFlow> {
            every { signUpUri } returns null
            every { this@mockk.signInUri } returns signInUri
        }
        coEvery { mapper.appendState(session, signInUri) } returns signInUri

        val result = mapper.toRedirectUri(session, flow, InteractiveFlowStep.SignUp)

        assertEquals(signInUri, result)
    }

    @Test
    fun `toRedirectUri - ValidateClaims appends the media query parameter`() = runTest {
        val validateUri = URI.create("https://www.example.com/validate")
        val session = mockk<OnGoingInteractiveFlowSession>()
        val flow = mockk<InteractiveFlow> { every { validateClaimsUri } returns validateUri }
        coEvery { mapper.appendState(session, any()) } returnsArgument 1

        val result = mapper.toRedirectUri(
            session, flow, InteractiveFlowStep.ValidateClaims(ValidationCodeMedia.EMAIL)
        )

        assertEquals(validateUri.host, result.host)
        assertEquals(validateUri.path, result.path)
        assertEquals("media=email", result.query)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `toRedirectUri - Complete for AUTHORIZATION_CODE hands its code to the client without internal state`() = runTest {
        val session = mockk<CompletedInteractiveFlowSession> {
            every { handBack } returns authorizationCodeHandBack
        }
        val authorizationCode = mockk<AuthorizationCode> { every { code } returns "authorizationCode" }
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
        coEvery { authorizationCodeManager.fetchCode(session) } returns authorizationCode

        val result = mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Complete)

        assertEquals("https://www.example.com?state=clientState&code=authorizationCode", result.toString())
    }

    @Test
    fun `toRedirectUri - Complete for PLAIN returns to the return URI naming the flow and the return state`() =
        runTest {
            val session = mockk<CompletedInteractiveFlowSession> {
                every { handBack } returns plainHandBack
                every { initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT
            }

            val result = mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Complete)

            assertEquals(
                "https://client.example.com/account?sympauthy_flow=mfa_enrollment&sympauthy_return_state=xyz",
                result.toString()
            )
            coVerify(exactly = 0) { authorizationCodeManager.fetchCode(any()) }
        }

    @Test
    fun `toRedirectUri - AlreadyCompleted maps to errorUri with state appended`() = runTest {
        val uri = URI.create("https://www.example.com/error")
        val session = mockk<CompletedInteractiveFlowSession>()
        val flow = mockk<InteractiveFlow> { every { errorUri } returns uri }
        coEvery { mapper.appendState(session, uri) } returns uri

        val result = mapper.toRedirectUri(session, flow, InteractiveFlowStep.AlreadyCompleted)

        assertEquals(uri, result)
        coVerify(exactly = 0) { authorizationCodeManager.fetchCode(any()) }
    }

    @Test
    fun `toRedirectUri - Complete throws when the session is not completed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()

        val exception = assertThrows<BusinessException> {
            mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Complete)
        }
        assertEquals("flow.redirect.unhandled_status", exception.detailsId)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `toRedirectUri - Cancel for AUTHORIZATION_CODE returns the OAuth2 access_denied response without a code`() = runTest {
        val session = mockk<CancelledInteractiveFlowSession> {
            every { handBack } returns authorizationCodeHandBack
        }
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns oauth2

        val result = mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Cancel)

        assertEquals("https://www.example.com?error=access_denied&state=clientState", result.toString())
        coVerify(exactly = 0) { authorizationCodeManager.fetchCode(any()) }
    }

    @Test
    fun `toRedirectUri - Cancel for PLAIN returns to the return URI saying the flow was cancelled`() = runTest {
        val session = mockk<CancelledInteractiveFlowSession> {
            every { handBack } returns plainHandBack.copy(returnState = null)
            every { initiatingPurpose } returns InteractiveFlowPurpose.LINK_PROVIDER
        }

        val result = mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Cancel)

        assertEquals(
            "https://client.example.com/account?sympauthy_flow=link_provider" +
                "&sympauthy_flow_error=auth.interactive_flow_session.cancelled",
            result.toString()
        )
    }

    @Test
    fun `getLeaveUriOrNull - Returns a failed PLAIN session to the return URI with the code it failed with`() =
        runTest {
            val session = mockk<FailedInteractiveFlowSession> {
                every { handBack } returns plainHandBack
                every { initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT
                every { errorDetailsId } returns "auth.interactive_flow_session.validate.expired"
            }

            val result = mapper.getLeaveUriOrNull(session)

            assertEquals(
                "https://client.example.com/account?sympauthy_flow=mfa_enrollment" +
                    "&sympauthy_flow_error=auth.interactive_flow_session.validate.expired&sympauthy_return_state=xyz",
                result.toString()
            )
        }

    @Test
    fun `getLeaveUriOrNull - Returns a completed PLAIN session to the return URI without an error`() = runTest {
        val session = mockk<CompletedInteractiveFlowSession> {
            every { handBack } returns plainHandBack.copy(returnState = null)
            every { initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT
        }

        val result = mapper.getLeaveUriOrNull(session)

        assertEquals("https://client.example.com/account?sympauthy_flow=mfa_enrollment", result.toString())
    }

    @Test
    fun `getLeaveUriOrNull - Answers a failed authorization with the error its failure maps to`() = runTest {
        val session = mockk<FailedInteractiveFlowSession> {
            every { handBack } returns authorizationCodeHandBack
            every { errorDetailsId } returns "scope.parse_requested.not_allowed"
        }
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns null

        val result = mapper.getLeaveUriOrNull(session)

        assertEquals("https://www.example.com?error=invalid_scope", result.toString())
    }

    @Test
    fun `getLeaveUriOrNull - Sends nowhere a running session, a completed authorization or a refused one`() =
        runTest {
            val onGoing = mockk<OnGoingInteractiveFlowSession> { every { handBack } returns plainHandBack }
            val completedAuthorization = mockk<CompletedInteractiveFlowSession> {
                every { handBack } returns authorizationCodeHandBack
            }
            val refusedAuthorization = mockk<FailedInteractiveFlowSession> { every { handBack } returns null }

            assertNull(mapper.getLeaveUriOrNull(onGoing))
            assertNull(mapper.getLeaveUriOrNull(completedAuthorization))
            assertNull(mapper.getLeaveUriOrNull(refusedAuthorization))
        }

    @Test
    fun `getAuthorizationErrorCode - Names what the client got wrong, and access_denied for anything else`() {
        assertEquals(INVALID_SCOPE, mapper.getAuthorizationErrorCode("scope.audience_mismatch"))
        assertEquals(UNAUTHORIZED_CLIENT, mapper.getAuthorizationErrorCode("authorize.unauthorized_grant_type"))
        assertEquals(INVALID_REQUEST, mapper.getAuthorizationErrorCode("authorize.pkce.missing_code_challenge"))
        assertEquals(INVALID_REQUEST, mapper.getAuthorizationErrorCode("claim.parse_requested.invalid"))
        assertEquals(INVALID_REQUEST, mapper.getAuthorizationErrorCode("invitation.expired"))
        assertEquals(ACCESS_DENIED, mapper.getAuthorizationErrorCode("auth.interactive_flow_session.validate.expired"))
    }

    @Test
    fun `getCancelUriOrNull - Links to the cancel endpoint with the state`() = runTest {
        val session = mockk<FailedInteractiveFlowSession> { every { handBack } returns authorizationCodeHandBack }
        coEvery { sessionManager.encodeState(session) } returns "encodedState"

        val result = urlsMapper().getCancelUriOrNull(session)

        assertEquals("https://auth.example.com/api/v1/flow/cancel?state=encodedState", result.toString())
    }

    @Test
    fun `getCancelUriOrNull - Offers no way out of a completed authorization or a session without hand-back`() =
        runTest {
            val completedAuthorization = mockk<CompletedInteractiveFlowSession> {
                every { handBack } returns authorizationCodeHandBack
            }
            val refusedAuthorization = mockk<FailedInteractiveFlowSession> { every { handBack } returns null }

            assertNull(urlsMapper().getCancelUriOrNull(completedAuthorization))
            assertNull(urlsMapper().getCancelUriOrNull(refusedAuthorization))
        }

    @Test
    fun `getCancelUriOrNull - Offers a way out of a completed PLAIN session`() = runTest {
        val session = mockk<CompletedInteractiveFlowSession> { every { handBack } returns plainHandBack }
        coEvery { sessionManager.encodeState(session) } returns "encodedState"

        val result = urlsMapper().getCancelUriOrNull(session)

        assertEquals("https://auth.example.com/api/v1/flow/cancel?state=encodedState", result.toString())
    }

    @Test
    fun `toRedirectUri - Cancel throws when the session is not cancelled`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()

        val exception = assertThrows<BusinessException> {
            mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Cancel)
        }
        assertEquals("flow.redirect.unhandled_status", exception.detailsId)
    }

    @Test
    fun `appendState - Encodes the state and adds it as a query param`() = runTest {
        val uri = URI.create("https://www.example.com")
        val session = mockk<InteractiveFlowSession>()
        coEvery { sessionManager.encodeState(session) } returns "encodedState"

        val result = mapper.appendState(session, uri)

        assertEquals("https://www.example.com?state=encodedState", result.toString())
    }
}
