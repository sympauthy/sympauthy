package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.claim.StandardClaim
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ConsentAwareClaimManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @SpyK
    @InjectMockKs
    lateinit var manager: ConsentAwareClaimManager

@Test
    fun `listCollectableClaimsWithScopes - Return claims writable by user within consented scopes`() {
        val scope1 = "scope1"
        val scope2 = "scope2"

        val claim1 = mockk<StandardClaim> {
            every { canBeWrittenByUser(any()) } answers { (firstArg<List<String>>()).contains(scope1) }
        }
        val claim2 = mockk<StandardClaim> {
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

        val identifierClaim = mockk<StandardClaim> {
            every { canBeWrittenByUser(any()) } returns true
        }
        val regularClaim = mockk<StandardClaim> {
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
        val claim1 = mockk<StandardClaim> {
            every { canBeWrittenByUser(any()) } returns false
        }

        every { claimManager.listCollectableClaims() } returns listOf(claim1)
        every { claimManager.listIdentifierClaims() } returns emptyList()

        val result = manager.listCollectableClaimsWithScopes(listOf("unrelated_scope"))

        assertTrue(result.isEmpty())
    }

@Test
    fun `listCollectableClaimsByAttempt - Return empty list for FailedAuthorizeAttempt`() {
        val attempt = mockk<FailedAuthorizeAttempt>()

        val result = manager.listCollectableClaimsByAttempt(attempt)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listCollectableClaimsByAttempt - Return empty list for OnGoingAuthorizeAttempt with no consentedScopes`() {
        val attempt = mockk<OnGoingAuthorizeAttempt> {
            every { consentedScopes } returns null
        }

        val result = manager.listCollectableClaimsByAttempt(attempt)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listCollectableClaimsByAttempt - Return claims for OnGoingAuthorizeAttempt with consentedScopes`() {
        val consentedScopes = listOf("scope1")
        val claim1 = mockk<StandardClaim>()

        val attempt = mockk<OnGoingAuthorizeAttempt> {
            every { this@mockk.consentedScopes } returns consentedScopes
        }

        every { manager.listCollectableClaimsWithScopes(consentedScopes) } returns listOf(claim1)

        val result = manager.listCollectableClaimsByAttempt(attempt)

        assertEquals(1, result.size)
        assertSame(claim1, result[0])
    }

    @Test
    fun `listCollectableClaimsByAttempt - Return claims for CompletedAuthorizeAttempt`() {
        val consentedScopes = listOf("scope1")
        val claim1 = mockk<StandardClaim>()

        val attempt = mockk<CompletedAuthorizeAttempt> {
            every { this@mockk.consentedScopes } returns consentedScopes
        }

        every { manager.listCollectableClaimsWithScopes(consentedScopes) } returns listOf(claim1)

        val result = manager.listCollectableClaimsByAttempt(attempt)

        assertEquals(1, result.size)
        assertSame(claim1, result[0])
    }
}
