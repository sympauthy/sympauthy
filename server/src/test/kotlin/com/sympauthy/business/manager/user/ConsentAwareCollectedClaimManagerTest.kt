package com.sympauthy.business.manager.user

import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.CustomClaim
import com.sympauthy.business.model.user.claim.StandardClaim
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
class ConsentAwareCollectedClaimManagerTest {

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @SpyK
    @InjectMockKs
    lateinit var manager: ConsentAwareCollectedClaimManager

    @Test
    fun `findByUserIdAndReadableByScopes - Return only claims readable by consented scopes`() = runTest {
        val userId = UUID.randomUUID()
        val scope1 = "scope1"
        val scope2 = "scope2"

        val claim1 = mockk<StandardClaim> {
            every { readScopes } returns setOf(scope1)
            every { canBeRead(any()) } answers { callOriginal() }
        }
        val claim2 = mockk<StandardClaim> {
            every { readScopes } returns setOf(scope2)
            every { canBeRead(any()) } answers { callOriginal() }
        }

        val collectedClaim1 = mockk<CollectedClaim> {
            every { claim } returns claim1
        }
        val collectedClaim2 = mockk<CollectedClaim> {
            every { claim } returns claim2
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedClaim1, collectedClaim2)

        val result = manager.findByUserIdAndReadableByScopes(userId, listOf(scope1))

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `findByUserIdAndReadableByScopes - Always include custom claims`() = runTest {
        val userId = UUID.randomUUID()
        val scope1 = "scope1"

        val standardClaim = mockk<StandardClaim> {
            every { readScopes } returns setOf(scope1)
            every { canBeRead(any()) } answers { callOriginal() }
        }
        val customClaim = mockk<CustomClaim> {
            every { readScopes } returns emptySet()
            every { canBeRead(any()) } answers { callOriginal() }
        }

        val collectedStandard = mockk<CollectedClaim> {
            every { claim } returns standardClaim
        }
        val collectedCustom = mockk<CollectedClaim> {
            every { claim } returns customClaim
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedStandard, collectedCustom)

        val result = manager.findByUserIdAndReadableByScopes(userId, listOf(scope1))

        assertEquals(2, result.count())
        assertTrue(result.contains(collectedStandard))
        assertTrue(result.contains(collectedCustom))
    }

    @Test
    fun `findByAttempt - Return empty list for FailedAuthorizeAttempt`() = runTest {
        val attempt = mockk<FailedAuthorizeAttempt>()

        val result = manager.findByAttempt(attempt)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findByAttempt - Return empty list for OnGoingAuthorizeAttempt with no userId`() = runTest {
        val attempt = mockk<OnGoingAuthorizeAttempt> {
            every { userId } returns null
        }

        val result = manager.findByAttempt(attempt)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findByAttempt - Return claims for OnGoingAuthorizeAttempt with userId and consentedScopes`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("scope1", "scope2")
        val requestedScopes = listOf("scope1", "scope2", "scope3")
        val collectedClaim1 = mockk<CollectedClaim>()

        val attempt = mockk<OnGoingAuthorizeAttempt> {
            every { this@mockk.userId } returns userId
            every { this@mockk.consentedScopes } returns consentedScopes
            every { this@mockk.requestedScopes } returns requestedScopes
        }

        coEvery { manager.findByUserIdAndReadableByScopes(userId, consentedScopes) } returns listOf(collectedClaim1)

        val result = manager.findByAttempt(attempt)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `findByAttempt - Return empty list for OnGoingAuthorizeAttempt with userId but no consentedScopes`() = runTest {
        val attempt = mockk<OnGoingAuthorizeAttempt> {
            every { userId } returns UUID.randomUUID()
            every { consentedScopes } returns null
        }

        val result = manager.findByAttempt(attempt)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findByAttempt - Return claims for CompletedAuthorizeAttempt`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("scope1", "scope2")
        val collectedClaim1 = mockk<CollectedClaim>()

        val attempt = mockk<CompletedAuthorizeAttempt> {
            every { this@mockk.userId } returns userId
            every { this@mockk.consentedScopes } returns consentedScopes
        }

        coEvery { manager.findByUserIdAndReadableByScopes(userId, consentedScopes) } returns listOf(collectedClaim1)

        val result = manager.findByAttempt(attempt)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `update - Filter updates that can be written with consented scope`() = runTest {
        val scope1 = "scope1"
        val claim1 = mockk<StandardClaim> {
            every { writeScopes } returns setOf(scope1)
            every { canBeWritten(any()) } answers { callOriginal() }
            every { readScopes } returns setOf(scope1)
            every { canBeRead(any()) } answers { callOriginal() }
        }
        val update1 = mockk<CollectedClaimUpdate> {
            every { claim } returns claim1
        }

        val scope2 = "scope2"
        val claim2 = mockk<StandardClaim> {
            every { writeScopes } returns setOf(scope2)
            every { canBeWritten(any()) } answers { callOriginal() }
        }
        val update2 = mockk<CollectedClaimUpdate> {
            every { claim } returns claim2
        }

        val user = mockk<User>()
        val consentedScopes = listOf(scope1)

        val collectedClaim1 = mockk<CollectedClaim> {
            every { claim } returns claim1
        }

        // Only update1 should pass the filter
        coEvery { collectedClaimManager.applyUpdates(user, listOf(update1)) } returns listOf(collectedClaim1)

        val result = manager.update(user, listOf(update1, update2), consentedScopes)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }
}
