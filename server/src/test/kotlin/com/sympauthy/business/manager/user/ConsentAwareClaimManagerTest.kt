package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.claim.Claim
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

    @SpyK
    @InjectMockKs
    lateinit var manager: ConsentAwareClaimManager

    private fun oauth2(consentedScopes: List<String>?) = InteractiveFlowSessionOAuth2(
        sessionId = UUID.randomUUID(),
        clientId = "test-client",
        redirectUri = "https://example.com/callback",
        requestedScopes = emptyList(),
        consentedScopes = consentedScopes
    )

    @Test
    fun `listCollectableClaimsWithScopes - Return claims writable by user within consented scopes`() {
        val scope1 = "scope1"
        val scope2 = "scope2"

        val claim1 = mockk<Claim> {
            every { canBeWrittenByUser(any()) } answers { (firstArg<List<String>>()).contains(scope1) }
        }
        val claim2 = mockk<Claim> {
            every { canBeWrittenByUser(any()) } answers { (firstArg<List<String>>()).contains(scope2) }
        }

        every { claimManager.listCollectableClaims() } returns listOf(claim1, claim2)
        every { claimManager.listIdentifierClaims() } returns emptyList()

        val result = manager.listCollectableClaimsWithScopes(listOf(scope1))

        assertEquals(1, result.size)
        assertSame(claim1, result[0])
    }

    @Test
    fun `listCollectableClaimsWithScopes - Exclude identifier claims`() {
        val scope1 = "scope1"

        // An identifier claim is dropped before anything asks whether the user could write it.
        val identifierClaim = mockk<Claim>()
        val regularClaim = mockk<Claim> {
            every { canBeWrittenByUser(any()) } returns true
        }

        every { claimManager.listCollectableClaims() } returns listOf(identifierClaim, regularClaim)
        every { claimManager.listIdentifierClaims() } returns listOf(identifierClaim)

        val result = manager.listCollectableClaimsWithScopes(listOf(scope1))

        assertEquals(1, result.size)
        assertSame(regularClaim, result[0])
    }

    @Test
    fun `listCollectableClaimsWithScopes - Return empty when no claims match scopes`() {
        val claim1 = mockk<Claim> {
            every { canBeWrittenByUser(any()) } returns false
        }

        every { claimManager.listCollectableClaims() } returns listOf(claim1)
        every { claimManager.listIdentifierClaims() } returns emptyList()

        val result = manager.listCollectableClaimsWithScopes(listOf("unrelated_scope"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listCollectableClaimsBySession - Return empty list for FailedInteractiveFlowSession`() = runTest {
        val session = mockk<FailedInteractiveFlowSession>()

        val result = manager.listCollectableClaimsBySession(session)

        assertTrue(result.isEmpty())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `listCollectableClaimsBySession - Return empty list for OnGoingInteractiveFlowSession with no consentedScopes`() =
        runTest {
            val session = mockk<OnGoingInteractiveFlowSession>()
            coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2(consentedScopes = null)

            val result = manager.listCollectableClaimsBySession(session)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `listCollectableClaimsBySession - Return claims for OnGoingInteractiveFlowSession with consentedScopes`() =
        runTest {
            val consentedScopes = listOf("scope1")
            val claim1 = mockk<Claim>()

            val session = mockk<OnGoingInteractiveFlowSession>()
            coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2(consentedScopes = consentedScopes)

            every { manager.listCollectableClaimsWithScopes(consentedScopes) } returns listOf(claim1)

            val result = manager.listCollectableClaimsBySession(session)

            assertEquals(1, result.size)
            assertSame(claim1, result[0])
        }

    @Test
    fun `listCollectableClaimsBySession - Return claims for CompletedInteractiveFlowSession`() = runTest {
        val consentedScopes = listOf("scope1")
        val claim1 = mockk<Claim>()

        val session = mockk<CompletedInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2(consentedScopes = consentedScopes)

        every { manager.listCollectableClaimsWithScopes(consentedScopes) } returns listOf(claim1)

        val result = manager.listCollectableClaimsBySession(session)

        assertEquals(1, result.size)
        assertSame(claim1, result[0])
    }
}
