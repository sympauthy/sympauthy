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
        required: Boolean = false,
        collectedInFlow: Boolean = true,
        readableByClient: Boolean = true,
        publishedIn: Set<ClaimPublicationPlace> = ClaimPublicationPlace.entries.toSet()
    ) = Claim(
        id = "claim_$scope",

        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = required,
        generated = false,
        collectedInFlow = collectedInFlow,
        allowedValues = null,
        audienceId = audienceId,
        publishedIn = publishedIn,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = scope,
                readableByPerson = true,
                collectedInFlow = collectedInFlow,
                writableByPerson = false,
                readableByClient = readableByClient,
                writableByClient = true,
                writeMaxAuthenticationAge = null
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
        collectedInFlow = false,
        allowedValues = null,
        publishedIn = ClaimPublicationPlace.entries.toSet(),
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByPerson = false,
                collectedInFlow = false,
                writableByPerson = false,
                readableByClient = false,
                writableByClient = false,
                writeMaxAuthenticationAge = null
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = listOf("users:claims:write")
            )
        )
    )

    @Test
    fun `findByUserIdAndReadableByPerson - Return only claims readable by consented scopes`() = runTest {
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

        val result = manager.findByUserIdAndReadableByPerson(userId, AUDIENCE, listOf(scope1))

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `findByUserIdAndReadableByPerson - Exclude claims not readable by user`() = runTest {
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

        val result = manager.findByUserIdAndReadableByPerson(userId, AUDIENCE, listOf(scope1))

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
    fun `findByUserIdAndReadableByPerson - Leave out a claim restricted to another audience`() = runTest {
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

        val result = manager.findByUserIdAndReadableByPerson(userId, "storefront", listOf(scope))

        assertEquals(1, result.count())
        assertSame(collectedShared, result[0])
    }

    @Test
    fun `findByUserIdAndReadableByClientAndPublishedIn - Leave out a claim the place does not carry`() =
        runTest {
            val userId = UUID.randomUUID()
            val scope = "scope1"

            val carried = claimWithConsentScope(scope)
            val withheld = claimWithConsentScope(scope, publishedIn = setOf(ClaimPublicationPlace.USERINFO))
            val collectedCarried = mockk<CollectedClaim> {
                every { claim } returns carried
            }
            val collectedWithheld = mockk<CollectedClaim> {
                every { claim } returns withheld
            }

            coEvery { collectedClaimManager.findByUserId(userId) } returns
                    listOf(collectedCarried, collectedWithheld)

            val result = manager.findByUserIdAndReadableByClientAndPublishedIn(
                userId, AUDIENCE, ClaimPublicationPlace.ACCESS_TOKEN, listOf(scope)
            )

            assertEquals(1, result.count())
            assertSame(collectedCarried, result[0])
        }

    @Test
    fun `findByUserIdAndReadableByClientAndPublishedIn - Leave out a claim the ACL refuses`() = runTest {
        val userId = UUID.randomUUID()

        val refused = claimWithConsentScope("scope1")
        val collectedRefused = mockk<CollectedClaim> {
            every { claim } returns refused
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedRefused)

        val result = manager.findByUserIdAndReadableByClientAndPublishedIn(
            userId, AUDIENCE, ClaimPublicationPlace.ACCESS_TOKEN, emptyList()
        )

        assertTrue(result.isEmpty())
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
    fun `findByUserIdAndCollectedInFlow - Answer a claim the flow collects and no client may read`() = runTest {
        val userId = UUID.randomUUID()
        val scope = "scope1"
        val hidden = claimWithConsentScope(scope, readableByClient = false)
        val collected = mockk<CollectedClaim> {
            every { claim } returns hidden
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        val result = manager.findByUserIdAndCollectedInFlow(userId, AUDIENCE, listOf(scope))

        assertEquals(1, result.count())
        assertSame(collected, result[0])
    }

    @Test
    fun `findByUserIdAndCollectedInFlow - Leave out a claim the flow does not collect`() = runTest {
        val userId = UUID.randomUUID()
        val scope = "scope1"
        val collected = mockk<CollectedClaim> {
            every { claim } returns claimWithConsentScope(scope, collectedInFlow = false)
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        assertTrue(manager.findByUserIdAndCollectedInFlow(userId, AUDIENCE, listOf(scope)).isEmpty())
    }

    @Test
    fun `findByUserIdAndCollectedInFlow - Leave out a claim outside the consented scopes`() = runTest {
        val userId = UUID.randomUUID()
        val collected = mockk<CollectedClaim> {
            every { claim } returns claimWithConsentScope("scope1")
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        assertTrue(manager.findByUserIdAndCollectedInFlow(userId, AUDIENCE, listOf("scope2")).isEmpty())
    }

    @Test
    fun `findByUserIdAndCollectedInFlow - Leave out a claim restricted to another audience`() = runTest {
        val userId = UUID.randomUUID()
        val scope = "scope1"
        val collected = mockk<CollectedClaim> {
            every { claim } returns claimWithConsentScope(scope, audienceId = "billing")
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        assertTrue(manager.findByUserIdAndCollectedInFlow(userId, "storefront", listOf(scope)).isEmpty())
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
            manager.findByUserIdAndCollectedInFlow(userId, AUDIENCE, consentedScopes)
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
            manager.findByUserIdAndCollectedInFlow(userId, AUDIENCE, consentedScopes)
        } returns listOf(collectedClaim1)

        val result = manager.findBySession(session)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `areAllRequiredClaimsCollectedInFlow - A required claim of the audience must be collected`() = runTest {
        val scope = "scope1"
        val required = claimWithConsentScope(scope, audienceId = "storefront", required = true)

        every { claimManager.listRequiredClaims() } returns listOf(required)

        assertFalse(
            manager.areAllRequiredClaimsCollectedInFlow(emptyList(), "storefront", listOf(scope))
        )
    }

    @Test
    fun `areAllRequiredClaimsCollectedInFlow - A required claim no client may read reads as collected`() = runTest {
        val userId = UUID.randomUUID()
        val scope = "scope1"
        val required = claimWithConsentScope(
            scope,
            audienceId = "storefront",
            required = true,
            readableByClient = false
        )
        val collected = mockk<CollectedClaim> {
            every { claim } returns required
        }

        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)
        every { claimManager.listRequiredClaims() } returns listOf(required)

        val readBack = manager.findByUserIdAndCollectedInFlow(userId, "storefront", listOf(scope))

        assertTrue(
            manager.areAllRequiredClaimsCollectedInFlow(readBack, "storefront", listOf(scope))
        )
    }

    @Test
    fun `areAllRequiredClaimsCollectedInFlow - A required claim restricted to another audience is not required`() =
        runTest {
            val scope = "scope1"
            val required = claimWithConsentScope(scope, audienceId = "billing", required = true)

            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertTrue(
                manager.areAllRequiredClaimsCollectedInFlow(emptyList(), "storefront", listOf(scope))
            )
        }

    @Test
    fun `areAllRequiredClaimsCollectedInFlow - A required claim restricted to no audience is every audience's`() =
        runTest {
            val scope = "scope1"
            val required = claimWithConsentScope(scope, required = true)

            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertFalse(
                manager.areAllRequiredClaimsCollectedInFlow(emptyList(), "storefront", listOf(scope))
            )
        }

    @Test
    fun `areAllRequiredClaimsCollectedInFlow - A required claim outside the consented scopes is not required`() =
        runTest {
            val required = claimWithConsentScope("scope1", audienceId = "storefront", required = true)

            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertTrue(
                manager.areAllRequiredClaimsCollectedInFlow(emptyList(), "storefront", listOf("scope2"))
            )
        }

    @Test
    fun `updateInFlow - Apply only updates for collectable claims`() = runTest {
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
            consentAwareClaimManager.listClaimsCollectedInFlowWithScopes(AUDIENCE, consentedScopes)
        } returns listOf(claim1)
        coEvery { collectedClaimManager.applyUpdates(user, listOf(update1)) } returns listOf(collectedClaim1)

        val result = manager.updateInFlow(user, AUDIENCE, listOf(update1, update2), consentedScopes)

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
