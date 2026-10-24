package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.claim.EnabledClaim
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class ConsentAwareClaimManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var claimAclManager: ClaimAclManager

    @SpyK
    @InjectMockKs
    lateinit var manager: ConsentAwareClaimManager

    private val audience = Audience(id = AUDIENCE, tokenAudience = AUDIENCE)

    private val client = Client(
        id = "test-client",
        secret = null,
        audience = audience,
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
    )

    private companion object {
        const val AUDIENCE = "test-audience"
    }

    private fun oauth2(consentedScopes: List<String>?) = InteractiveFlowSessionOAuth2(
        sessionId = UUID.randomUUID(),
        clientId = "test-client",
        redirectUri = "https://example.com/callback",
        requestedScopes = emptyList(),
        consentedScopes = consentedScopes
    )

    @Test
    fun `listClaimsCollectedInFlowWithScopes - Return claims writable by user within consented scopes`() {
        val scope1 = "scope1"
        val scope2 = "scope2"

        val claim1 = mockk<EnabledClaim> {
            every { belongsToAudience(AUDIENCE) } returns true
        }
        val claim2 = mockk<EnabledClaim> {
            every { belongsToAudience(AUDIENCE) } returns true
        }
        every { claimAclManager.isCollectedInFlow(claim1, client, any()) } answers {
            thirdArg<List<String>>().contains(scope1)
        }
        every { claimAclManager.isCollectedInFlow(claim2, client, any()) } answers {
            thirdArg<List<String>>().contains(scope2)
        }

        every { claimManager.listClaimsCollectedInFlow() } returns listOf(claim1, claim2)
        every { claimManager.listIdentifierClaims() } returns emptyList()

        val result = manager.listClaimsCollectedInFlowWithScopes(client, listOf(scope1))

        assertEquals(1, result.size)
        assertSame(claim1, result[0])
    }

    @Test
    fun `listClaimsCollectedInFlowWithScopes - Exclude identifier claims`() {
        val scope1 = "scope1"

        // An identifier claim is dropped before anything asks which audience it is for or whether the user
        // could write it.
        val identifierClaim = mockk<EnabledClaim>()
        val regularClaim = mockk<EnabledClaim> {
            every { belongsToAudience(AUDIENCE) } returns true
        }
        every { claimAclManager.isCollectedInFlow(regularClaim, client, any()) } returns true

        every { claimManager.listClaimsCollectedInFlow() } returns listOf(identifierClaim, regularClaim)
        every { claimManager.listIdentifierClaims() } returns listOf(identifierClaim)

        val result = manager.listClaimsCollectedInFlowWithScopes(client, listOf(scope1))

        assertEquals(1, result.size)
        assertSame(regularClaim, result[0])
    }

    @Test
    fun `listClaimsCollectedInFlowWithScopes - Return empty when no claims match scopes`() {
        val claim1 = mockk<EnabledClaim> {
            every { belongsToAudience(AUDIENCE) } returns true
        }
        every { claimAclManager.isCollectedInFlow(claim1, client, any()) } returns false

        every { claimManager.listClaimsCollectedInFlow() } returns listOf(claim1)
        every { claimManager.listIdentifierClaims() } returns emptyList()

        val result = manager.listClaimsCollectedInFlowWithScopes(client, listOf("unrelated_scope"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listClaimsCollectedInFlowBySession - Return empty list for FailedInteractiveFlowSession`() = runTest {
        val session = mockk<FailedInteractiveFlowSession>()

        val result = manager.listClaimsCollectedInFlowBySession(session)

        assertTrue(result.isEmpty())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `listClaimsCollectedInFlowBySession - Return empty list for OnGoingInteractiveFlowSession with no consentedScopes`() =
        runTest {
            val session = mockk<OnGoingInteractiveFlowSession>()
            coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2(consentedScopes = null)

            val result = manager.listClaimsCollectedInFlowBySession(session)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `listClaimsCollectedInFlowBySession - Return claims for OnGoingInteractiveFlowSession with consentedScopes`() =
        runTest {
            val consentedScopes = listOf("scope1")
            val claim1 = mockk<EnabledClaim>()

            val session = mockk<OnGoingInteractiveFlowSession>()
            val oauth2 = oauth2(consentedScopes = consentedScopes)
            coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
            coEvery { oauth2Manager.getClient(oauth2) } returns client

            every { manager.listClaimsCollectedInFlowWithScopes(client, consentedScopes) } returns listOf(claim1)

            val result = manager.listClaimsCollectedInFlowBySession(session)

            assertEquals(1, result.size)
            assertSame(claim1, result[0])
        }

    @Test
    fun `listClaimsCollectedInFlowBySession - Return claims for CompletedInteractiveFlowSession`() = runTest {
        val consentedScopes = listOf("scope1")
        val claim1 = mockk<EnabledClaim>()

        val session = mockk<CompletedInteractiveFlowSession>()
        val oauth2 = oauth2(consentedScopes = consentedScopes)
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
        coEvery { oauth2Manager.getClient(oauth2) } returns client

        every { manager.listClaimsCollectedInFlowWithScopes(client, consentedScopes) } returns listOf(claim1)

        val result = manager.listClaimsCollectedInFlowBySession(session)

        assertEquals(1, result.size)
        assertSame(claim1, result[0])
    }
}
