package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.UrlsConfig
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class AuthorizationFlowManagerTest {

    @MockK
    lateinit var authorizationFlowsConfig: AuthorizationFlowsConfig

    @MockK
    lateinit var uncheckedUrlsConfig: UrlsConfig

    private val testAudience = Audience(id = "test-audience", tokenAudience = "test-audience")

    @InjectMockKs
    lateinit var manager: AuthorizationFlowManager

    // --- checkCanIssueToken tests ---

    @Test
    fun `checkCanIssueToken - Throws when session is null`() = runTest {
        val client = mockClient()

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(null, null, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when session is ongoing`() = runTest {
        val client = mockClient()
        val onGoingSession = createOnGoingSession(userId = UUID.randomUUID())

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(onGoingSession, null, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when session has failed`() = runTest {
        val client = mockClient()
        val failedSession = FailedInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            errorDetailsId = "some.error",
            errorDate = LocalDateTime.now()
        )

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(failedSession, null, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when session is expired`() = runTest {
        val client = mockClient("test-client")
        val completedSession = createCompletedSession(
            expirationDate = LocalDateTime.now().minusMinutes(1)
        )
        val oauth2 = oauth2Of(sessionId = completedSession.id, clientId = "test-client")

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(completedSession, oauth2, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when oauth2 record is missing`() = runTest {
        val client = mockClient("test-client")
        val completedSession = createCompletedSession()

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(completedSession, null, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when client does not match`() = runTest {
        val client = mockClient("other-client")
        val completedSession = createCompletedSession()
        val oauth2 = oauth2Of(sessionId = completedSession.id, clientId = "test-client")

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(completedSession, oauth2, client)
        }
        assertEquals("token.mismatching_client", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Returns completed session and oauth2 when valid`() = runTest {
        val client = mockClient("test-client")
        val completedSession = createCompletedSession()
        val oauth2 = oauth2Of(sessionId = completedSession.id, clientId = "test-client")

        val (resultSession, resultOAuth2) = manager.checkCanIssueToken(completedSession, oauth2, client)

        assertSame(completedSession, resultSession)
        assertSame(oauth2, resultOAuth2)
    }

    // --- helpers ---

    private fun mockClient(id: String = "test-client"): Client {
        return mockk {
            every { this@mockk.id } returns id
            every { audience } returns testAudience
        }
    }

    private fun createOnGoingSession(
        userId: UUID?
    ): OnGoingInteractiveFlowSession {
        return OnGoingInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = userId
        )
    }

    private fun createCompletedSession(
        expirationDate: LocalDateTime = LocalDateTime.now().plusHours(1)
    ): CompletedInteractiveFlowSession {
        val now = LocalDateTime.now()
        return CompletedInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = expirationDate,
            sessionDate = now,
            userId = UUID.randomUUID(),
            completeDate = now,
            successRedirectUri = URI.create("https://client.example.com/callback"),
            redirectType = InteractiveFlowRedirectType.AUTHORIZATION_CODE,
        )
    }

    private fun oauth2Of(
        sessionId: UUID,
        clientId: String = "client-id",
        consentedScopes: List<String>? = null,
        grantedScopes: List<String>? = null
    ): InteractiveFlowSessionOAuth2 {
        return InteractiveFlowSessionOAuth2(
            sessionId = sessionId,
            clientId = clientId,
            redirectUri = "https://example.com/callback",
            requestedScopes = emptyList(),
            state = "state",
            nonce = "nonce",
            consentedScopes = consentedScopes,
            consentedAt = consentedScopes?.let { LocalDateTime.now() },
            consentedBy = consentedScopes?.let { ConsentedBy.AUTO },
            grantedScopes = grantedScopes
        )
    }
}
