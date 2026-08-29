package com.sympauthy.api.controller.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.AuthorizationCode
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
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
        assertEquals("media=${ValidationCodeMedia.EMAIL.name}", result.query)
    }

    @Test
    fun `toRedirectUri - Complete for AUTHORIZATION_CODE generates a code and redirects to the client without internal state`() = runTest {
        val session = mockk<CompletedInteractiveFlowSession> {
            every { redirectType } returns InteractiveFlowRedirectType.AUTHORIZATION_CODE
            every { successRedirectUri } returns URI.create("https://www.example.com")
        }
        val oauth2 = InteractiveFlowSessionOAuth2(
            sessionId = UUID.randomUUID(),
            clientId = "test-client",
            redirectUri = "https://www.example.com",
            requestedScopes = emptyList(),
            state = "clientState"
        )
        val authorizationCode = mockk<AuthorizationCode> { every { code } returns "authorizationCode" }
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
        coEvery { authorizationCodeManager.generateCode(session) } returns authorizationCode

        val result = mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Complete)

        assertEquals("https://www.example.com?state=clientState&code=authorizationCode", result.toString())
    }

    @Test
    fun `toRedirectUri - Complete for PLAIN redirects to the success URI verbatim`() = runTest {
        val session = mockk<CompletedInteractiveFlowSession> {
            every { redirectType } returns InteractiveFlowRedirectType.PLAIN
            every { successRedirectUri } returns URI.create("https://client.example.com/enrolled")
        }

        val result = mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Complete)

        assertEquals("https://client.example.com/enrolled", result.toString())
        coVerify(exactly = 0) { authorizationCodeManager.generateCode(any()) }
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
    fun `toRedirectUri - Cancel for AUTHORIZATION_CODE returns the OAuth2 access_denied response without a code`() = runTest {
        val session = mockk<CancelledInteractiveFlowSession> {
            every { redirectType } returns InteractiveFlowRedirectType.AUTHORIZATION_CODE
            every { cancelRedirectUri } returns URI.create("https://www.example.com")
        }
        val oauth2 = InteractiveFlowSessionOAuth2(
            sessionId = UUID.randomUUID(),
            clientId = "test-client",
            redirectUri = "https://www.example.com",
            requestedScopes = emptyList(),
            state = "clientState"
        )
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2

        val result = mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Cancel)

        assertEquals("https://www.example.com?error=access_denied&state=clientState", result.toString())
        coVerify(exactly = 0) { authorizationCodeManager.generateCode(any()) }
    }

    @Test
    fun `toRedirectUri - Cancel for PLAIN redirects to the cancel URI verbatim`() = runTest {
        val session = mockk<CancelledInteractiveFlowSession> {
            every { redirectType } returns InteractiveFlowRedirectType.PLAIN
            every { cancelRedirectUri } returns URI.create("https://client.example.com/cancelled")
        }

        val result = mapper.toRedirectUri(session, mockk(), InteractiveFlowStep.Cancel)

        assertEquals("https://client.example.com/cancelled", result.toString())
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
