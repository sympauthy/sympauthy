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

    private fun claimWithConsentScope(
        scope: String,
        audienceId: String? = null,
        required: Boolean = false
    ) = Claim(
        id = "claim_$scope",

        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = required,
        generated = false,
        userInputted = true,
        allowedValues = null,
        audienceId = audienceId,
        publishedIn = ClaimPublication.entries.toSet(),
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
        publishedIn = ClaimPublication.entries.toSet(),
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

        val result = manager.findByUserIdAndReadableByUser(userId, AUDIENCE, listOf(scope1))

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

        val result = manager.findByUserIdAndReadableByUser(userId, AUDIENCE, listOf(scope1))

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

        val result = manager.findByUserIdAndReadableByClient(userId, AUDIENCE, listOf(scope1))

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `findByUserIdAndReadableByUser - Leave out a claim restricted to another audience`() = runTest {
        val userId = UUID.randomUUID()
        val scope = "scope1"

        val sharedClaim = claimWithConsentScope(scope)
        val billingClaim = claimWithConsentScope(scope, audienceId = "billing")

        val collectedShared = mockk<CollectedClaim> {
            every { claim } returns sharedClaim
        }
        val collectedBilling = mockk<CollectedClaim> {
            every { claim } returns billingClaim
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedShared, collectedBilling)

        val result = manager.findByUserIdAndReadableByUser(userId, "storefront", listOf(scope))

        assertEquals(1, result.count())
        assertSame(collectedShared, result[0])
    }

    @Test
    fun `findByUserIdAndReadableByClient - Leave out a claim restricted to another audience`() = runTest {
        val userId = UUID.randomUUID()
        val scope = "scope1"

        val sharedClaim = claimWithConsentScope(scope)
        val billingClaim = claimWithConsentScope(scope, audienceId = "billing")

        val collectedShared = mockk<CollectedClaim> {
            every { claim } returns sharedClaim
        }
        val collectedBilling = mockk<CollectedClaim> {
            every { claim } returns billingClaim
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedShared, collectedBilling)

        val result = manager.findByUserIdAndReadableByClient(userId, "storefront", listOf(scope))

        assertEquals(1, result.count())
        assertSame(collectedShared, result[0])
    }

    @Test
    fun `findByUserIdAndReadableByClient - Answer a claim restricted to the audience named`() = runTest {
        val userId = UUID.randomUUID()
        val scope = "scope1"

        val billingClaim = claimWithConsentScope(scope, audienceId = "billing")
        val collectedBilling = mockk<CollectedClaim> {
            every { claim } returns billingClaim
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedBilling)

        val result = manager.findByUserIdAndReadableByClient(userId, "billing", listOf(scope))

        assertEquals(1, result.count())
        assertSame(collectedBilling, result[0])
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
        val oauth2 = oauth2(consentedScopes = consentedScopes)
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
        coEvery { oauth2Manager.getAudienceId(oauth2) } returns AUDIENCE

        coEvery {
            manager.findByUserIdAndReadableByClient(userId, AUDIENCE, consentedScopes)
        } returns listOf(collectedClaim1)

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
        val oauth2 = oauth2(consentedScopes = consentedScopes)
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
        coEvery { oauth2Manager.getAudienceId(oauth2) } returns AUDIENCE

        coEvery {
            manager.findByUserIdAndReadableByClient(userId, AUDIENCE, consentedScopes)
        } returns listOf(collectedClaim1)

        val result = manager.findBySession(session)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `areAllRequiredClaimsCollectedByUser - A required claim of the audience must be collected`() = runTest {
        val scope = "scope1"
        val required = claimWithConsentScope(scope, audienceId = "storefront", required = true)

        every { claimManager.listRequiredClaims() } returns listOf(required)

        assertFalse(
            manager.areAllRequiredClaimsCollectedByUser(emptyList(), "storefront", listOf(scope))
        )
    }

    @Test
    fun `areAllRequiredClaimsCollectedByUser - A required claim restricted to another audience is not required`() =
        runTest {
            val scope = "scope1"
            val required = claimWithConsentScope(scope, audienceId = "billing", required = true)

            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertTrue(
                manager.areAllRequiredClaimsCollectedByUser(emptyList(), "storefront", listOf(scope))
            )
        }

    @Test
    fun `areAllRequiredClaimsCollectedByUser - A required claim restricted to no audience is every audience's`() =
        runTest {
            val scope = "scope1"
            val required = claimWithConsentScope(scope, required = true)

            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertFalse(
                manager.areAllRequiredClaimsCollectedByUser(emptyList(), "storefront", listOf(scope))
            )
        }

    @Test
    fun `areAllRequiredClaimsCollectedByUser - A required claim outside the consented scopes is not required`() =
        runTest {
            val required = claimWithConsentScope("scope1", audienceId = "storefront", required = true)

            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertTrue(
                manager.areAllRequiredClaimsCollectedByUser(emptyList(), "storefront", listOf("scope2"))
            )
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

        val collectedClaim1 = mockk<CollectedClaim>()

        every {
            consentAwareClaimManager.listCollectableClaimsWithScopes(AUDIENCE, consentedScopes)
        } returns listOf(claim1)
        coEvery { collectedClaimManager.applyUpdates(user, listOf(update1)) } returns listOf(collectedClaim1)

        val result = manager.updateByUser(user, AUDIENCE, listOf(update1, update2), consentedScopes)

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

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { collectedClaimManager.applyUpdates(user, listOf(update1)) } returns listOf(collectedClaim1)

        val result = manager.updateByClient(user, AUDIENCE, listOf(update1, update2), consentedScopes)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `updateByClient - Never write a claim restricted to another audience`() = runTest {
        val scope = "scope1"
        val billingClaim = claimWithConsentScope(scope, audienceId = "billing")
        val update = mockk<CollectedClaimUpdate> {
            every { claim } returns billingClaim
        }

        val user = mockk<User>()

        // applyUpdates is stubbed for the empty list alone: reaching the assertion is proof the refused
        // update never travelled to the write.
        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { collectedClaimManager.applyUpdates(user, emptyList()) } returns emptyList()

        val result = manager.updateByClient(user, "storefront", listOf(update), listOf(scope))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `updateByClient - Never write an identifier claim`() = runTest {
        val scope = "scope1"
        val emailClaim = claimWithConsentScope(scope)
        val update = mockk<CollectedClaimUpdate> {
            every { claim } returns emailClaim
        }

        val user = mockk<User>()

        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
        // applyUpdates is stubbed for the empty list alone: reaching the assertion is proof the refused
        // update never travelled to the write.
        coEvery { collectedClaimManager.applyUpdates(user, emptyList()) } returns emptyList()

        val result = manager.updateByClient(user, AUDIENCE, listOf(update), listOf(scope))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `updateByClient - Leave a claim restricted to another audience out of the answer`() = runTest {
        val scope = "scope1"
        val sharedClaim = claimWithConsentScope(scope)
        val billingClaim = claimWithConsentScope(scope, audienceId = "billing")
        val update = mockk<CollectedClaimUpdate> {
            every { claim } returns sharedClaim
        }

        val user = mockk<User>()
        val collectedShared = mockk<CollectedClaim> {
            every { claim } returns sharedClaim
        }
        val collectedBilling = mockk<CollectedClaim> {
            every { claim } returns billingClaim
        }

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery {
            collectedClaimManager.applyUpdates(user, listOf(update))
        } returns listOf(collectedShared, collectedBilling)

        val result = manager.updateByClient(user, "storefront", listOf(update), listOf(scope))

        assertEquals(1, result.count())
        assertSame(collectedShared, result[0])
    }
}
