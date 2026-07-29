package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.*
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
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var consentAwareClaimManager: ConsentAwareClaimManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @SpyK
    @InjectMockKs
    lateinit var manager: ConsentAwareCollectedClaimManager

    private fun oauth2(consentedScopes: List<String>?) = InteractiveFlowSessionOAuth2(
        sessionId = UUID.randomUUID(),
        clientId = "test-client",
        redirectUri = "https://example.com/callback",
        requestedScopes = emptyList(),
        consentedScopes = consentedScopes
    )

    private fun claimWithConsentScope(scope: String) = Claim(
        id = "claim_$scope",

        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = true,
        allowedValues = null,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = scope,
                readableByUser = true,
                writableByUser = true,
                readableByClient = true,
                writableByClient = true
            ),
            unconditional = UnconditionalAcl(emptyList(), emptyList())
        )
    )

    private fun customClaimNotReadableByUser() = Claim(
        id = "custom_field",

        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = false,
        allowedValues = null,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByUser = false,
                writableByUser = false,
                readableByClient = false,
                writableByClient = false
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = listOf("users:claims:write")
            )
        )
    )

    @Test
    fun `findByUserIdAndReadableByUser - Return only claims readable by consented scopes`() = runTest {
        val userId = UUID.randomUUID()
        val scope1 = "scope1"
        val scope2 = "scope2"

        val claim1 = claimWithConsentScope(scope1)
        val claim2 = claimWithConsentScope(scope2)

        val collectedClaim1 = mockk<CollectedClaim> {
            every { claim } returns claim1
        }
        val collectedClaim2 = mockk<CollectedClaim> {
            every { claim } returns claim2
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedClaim1, collectedClaim2)

        val result = manager.findByUserIdAndReadableByUser(userId, listOf(scope1))

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `findByUserIdAndReadableByUser - Exclude claims not readable by user`() = runTest {
        val userId = UUID.randomUUID()
        val scope1 = "scope1"

        val standardClaim = claimWithConsentScope(scope1)
        val customClaim = customClaimNotReadableByUser()

        val collectedStandard = mockk<CollectedClaim> {
            every { claim } returns standardClaim
        }
        val collectedCustom = mockk<CollectedClaim> {
            every { claim } returns customClaim
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedStandard, collectedCustom)

        val result = manager.findByUserIdAndReadableByUser(userId, listOf(scope1))

        assertEquals(1, result.count())
        assertSame(collectedStandard, result[0])
    }

    @Test
    fun `findByUserIdAndReadableByClient - Return only claims readable by consented scopes`() = runTest {
        val userId = UUID.randomUUID()
        val scope1 = "scope1"
        val scope2 = "scope2"

        val claim1 = claimWithConsentScope(scope1)
        val claim2 = claimWithConsentScope(scope2)

        val collectedClaim1 = mockk<CollectedClaim> {
            every { claim } returns claim1
        }
        val collectedClaim2 = mockk<CollectedClaim> {
            every { claim } returns claim2
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedClaim1, collectedClaim2)

        val result = manager.findByUserIdAndReadableByClient(userId, listOf(scope1))

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `findBySession - Return empty list for FailedInteractiveFlowSession`() = runTest {
        val session = mockk<FailedInteractiveFlowSession>()

        val result = manager.findBySession(session)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findBySession - Return empty list for OnGoingInteractiveFlowSession with no userId`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { userId } returns null
        }

        val result = manager.findBySession(session)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findBySession - Return claims for OnGoingInteractiveFlowSession with userId and consentedScopes`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("scope1", "scope2")
        val collectedClaim1 = mockk<CollectedClaim>()

        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
        }
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2(consentedScopes = consentedScopes)

        coEvery { manager.findByUserIdAndReadableByClient(userId, consentedScopes) } returns listOf(collectedClaim1)

        val result = manager.findBySession(session)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `findBySession - Return empty list for OnGoingInteractiveFlowSession with userId but no consentedScopes`() =
        runTest {
            val session = mockk<OnGoingInteractiveFlowSession> {
                every { userId } returns UUID.randomUUID()
            }
            coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2(consentedScopes = null)

            val result = manager.findBySession(session)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `findBySession - Return claims for CompletedInteractiveFlowSession`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("scope1", "scope2")
        val collectedClaim1 = mockk<CollectedClaim>()

        val session = mockk<CompletedInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
        }
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2(consentedScopes = consentedScopes)

        coEvery { manager.findByUserIdAndReadableByClient(userId, consentedScopes) } returns listOf(collectedClaim1)

        val result = manager.findBySession(session)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `updateByUser - Apply only updates for collectable claims`() = runTest {
        val scope1 = "scope1"
        val claim1 = claimWithConsentScope(scope1)
        val claim2 = claimWithConsentScope("scope2")
        val update1 = mockk<CollectedClaimUpdate> {
            every { claim } returns claim1
        }
        val update2 = mockk<CollectedClaimUpdate> {
            every { claim } returns claim2
        }

        val user = mockk<User>()
        val consentedScopes = listOf(scope1)

        val collectedClaim1 = mockk<CollectedClaim> {
            every { claim } returns claim1
        }

        every { consentAwareClaimManager.listCollectableClaimsWithScopes(consentedScopes) } returns listOf(claim1)
        coEvery { collectedClaimManager.applyUpdates(user, listOf(update1)) } returns listOf(collectedClaim1)

        val result = manager.updateByUser(user, listOf(update1, update2), consentedScopes)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `updateByClient - Filter updates that can be written by client`() = runTest {
        val scope1 = "scope1"
        val claim1 = claimWithConsentScope(scope1)
        val update1 = mockk<CollectedClaimUpdate> {
            every { claim } returns claim1
        }

        val scope2 = "scope2"
        val claim2 = claimWithConsentScope(scope2)
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

        val result = manager.updateByClient(user, listOf(update1, update2), consentedScopes)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }
}
