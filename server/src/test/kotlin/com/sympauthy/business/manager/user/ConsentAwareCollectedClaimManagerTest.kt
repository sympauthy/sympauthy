package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
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
import com.sympauthy.expression.ScopeIsConsentedFunction.Companion.FUNCTION_NAME as SCOPE_IS_CONSENTED
import com.sympauthy.expression.ScopeIsGrantedFunction.Companion.FUNCTION_NAME as SCOPE_IS_GRANTED

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
    var claimAclManager = ClaimAclManager()

    @SpyK
    @InjectMockKs
    lateinit var manager: ConsentAwareCollectedClaimManager

    private val audience = Audience(id = AUDIENCE, tokenAudience = AUDIENCE)
    private val storefront = Audience(id = "storefront", tokenAudience = "storefront")
    private val client = clientOf(audience)
    private val storefrontClient = clientOf(storefront)

    private fun clientOf(audience: Audience) = Client(
        id = "client-of-${audience.id}",
        secret = null,
        audience = audience,
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
    )

    /** What a token of the [client] carries, for a person who consented to [consentedScopes]. */
    private fun context(consentedScopes: List<String>) = ClaimAclContext(
        client = client,
        consentedScopes = consentedScopes,
        grantedScopes = emptyList(),
        authenticationDate = null,
        bound = false
    )

    /** The doors a token of the [client] comes through, for a person who consented to [consentedScopes]. */
    private fun doors(consentedScopes: List<String>) = claimAclManager.getTokenDoors(context(consentedScopes))

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
        writableByPerson: Boolean = false,
        publishedIn: Set<ClaimPublicationPlace> = ClaimPublicationPlace.entries.toSet(),
        publishedInWhenRequested: Set<ClaimPublicationPlace> = emptySet()
    ) = PersonalClaim(
        id = "claim_$scope",
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = required,
        allowedValues = null,
        audienceId = audienceId,
        publishedIn = publishedIn,
        publishedInWhenRequested = publishedInWhenRequested,
        acl = ClaimAclExpression("$SCOPE_IS_CONSENTED(\"$scope\")", consentedScopes = setOf(scope)).let {
            ClaimAcl(
                collectedInFlow = it.takeIf { collectedInFlow },
                readableByPerson = it,
                writableByPerson = it.takeIf { writableByPerson },
                readableByClient = it.takeIf { readableByClient },
                writableByClient = it
            )
        }
    )

    private fun customClaimNotReadableByUser() = PersonalClaim(
        id = "custom_field",
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        allowedValues = null,
        publishedIn = ClaimPublicationPlace.entries.toSet(),
        publishedInWhenRequested = emptySet(),
        acl = ClaimAcl(
            collectedInFlow = null,
            readableByPerson = null,
            writableByPerson = null,
            readableByClient = ClaimAclExpression("$SCOPE_IS_GRANTED(\"users:claims:read\")"),
            writableByClient = ClaimAclExpression("$SCOPE_IS_GRANTED(\"users:claims:write\")")
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

        val result = manager.findByUserIdAndReadableByPerson(userId, AUDIENCE, doors(listOf(scope1)))

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

        val result = manager.findByUserIdAndReadableByPerson(userId, AUDIENCE, doors(listOf(scope1)))

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

        val result = manager.findByUserIdAndReadableByClient(userId, AUDIENCE, doors(listOf(scope1)))

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

        val result = manager.findByUserIdAndReadableByPerson(userId, "storefront", doors(listOf(scope)))

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
                userId, AUDIENCE, ClaimPublicationPlace.ACCESS_TOKEN, doors(listOf(scope))
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
            userId, AUDIENCE, ClaimPublicationPlace.ACCESS_TOKEN, doors(emptyList())
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findByUserIdAndReadableByClientAndPublishedIn - Carry a claim the request named in an open channel`() =
        runTest {
            val userId = UUID.randomUUID()
            val scope = "scope1"

            val onRequest = claimWithConsentScope(
                scope,
                publishedIn = emptySet(),
                publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
            )
            val collectedOnRequest = mockk<CollectedClaim> {
                every { claim } returns onRequest
            }

            coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedOnRequest)

            val result = manager.findByUserIdAndReadableByClientAndPublishedIn(
                userId, AUDIENCE, ClaimPublicationPlace.ID_TOKEN, doors(listOf(scope)),
                requestedClaims = RequestedClaims(
                    idTokenClaimIds = setOf(onRequest.id),
                    userInfoClaimIds = emptySet()
                )
            )

            assertEquals(1, result.count())
            assertSame(collectedOnRequest, result[0])
        }

    @Test
    fun `findByUserIdAndReadableByClientAndPublishedIn - Leave out a claim the ACL refuses, request or no`() =
        runTest {
            val userId = UUID.randomUUID()

            val onRequest = claimWithConsentScope(
                "scope1",
                publishedIn = emptySet(),
                publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
            )
            val collectedOnRequest = mockk<CollectedClaim> {
                every { claim } returns onRequest
            }

            coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedOnRequest)

            val result = manager.findByUserIdAndReadableByClientAndPublishedIn(
                userId, AUDIENCE, ClaimPublicationPlace.ID_TOKEN, doors(emptyList()),
                requestedClaims = RequestedClaims(
                    idTokenClaimIds = setOf(onRequest.id),
                    userInfoClaimIds = emptySet()
                )
            )

            assertTrue(result.isEmpty())
        }

    @Test
    fun `findByUserIdAndReadableByClientAndPublishedIn - Leave out a claim of another audience, request or no`() =
        runTest {
            val userId = UUID.randomUUID()
            val scope = "scope1"

            val onRequest = claimWithConsentScope(
                scope,
                audienceId = "billing",
                publishedIn = emptySet(),
                publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
            )
            val collectedOnRequest = mockk<CollectedClaim> {
                every { claim } returns onRequest
            }

            coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collectedOnRequest)

            val result = manager.findByUserIdAndReadableByClientAndPublishedIn(
                userId, AUDIENCE, ClaimPublicationPlace.ID_TOKEN, doors(listOf(scope)),
                requestedClaims = RequestedClaims(
                    idTokenClaimIds = setOf(onRequest.id),
                    userInfoClaimIds = emptySet()
                )
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

        val result = manager.findByUserIdAndReadableByClient(userId, "storefront", doors(listOf(scope)))

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

        val result = manager.findByUserIdAndReadableByClient(userId, "billing", doors(listOf(scope)))

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

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        val result = manager.findByUserIdAndCollectedInFlow(userId, client, listOf(scope))

        assertEquals(1, result.count())
        assertSame(collected, result[0])
    }

    @Test
    fun `findByUserIdAndCollectedInFlow - Answer an identifier claim the flow does not mark collected`() = runTest {
        val userId = UUID.randomUUID()
        val identifier = claimWithConsentScope("scope1", collectedInFlow = false)
        val collected = mockk<CollectedClaim> {
            every { claim } returns identifier
        }

        every { claimManager.listIdentifierClaims() } returns listOf(identifier)
        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        val result = manager.findByUserIdAndCollectedInFlow(userId, storefrontClient, emptyList())

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

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        assertTrue(manager.findByUserIdAndCollectedInFlow(userId, client, listOf(scope)).isEmpty())
    }

    @Test
    fun `findByUserIdAndCollectedInFlow - Leave out a claim outside the consented scopes`() = runTest {
        val userId = UUID.randomUUID()
        val collected = mockk<CollectedClaim> {
            every { claim } returns claimWithConsentScope("scope1")
        }

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        assertTrue(manager.findByUserIdAndCollectedInFlow(userId, client, listOf("scope2")).isEmpty())
    }

    @Test
    fun `findByUserIdAndCollectedInFlow - Leave out a claim restricted to another audience`() = runTest {
        val userId = UUID.randomUUID()
        val scope = "scope1"
        val collected = mockk<CollectedClaim> {
            every { claim } returns claimWithConsentScope(scope, audienceId = "billing")
        }

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)

        assertTrue(manager.findByUserIdAndCollectedInFlow(userId, storefrontClient, listOf(scope)).isEmpty())
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
        coEvery { oauth2Manager.getClient(oauth2) } returns client

        coEvery {
            manager.findByUserIdAndCollectedInFlow(userId, client, consentedScopes)
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
        coEvery { oauth2Manager.getClient(oauth2) } returns client

        coEvery {
            manager.findByUserIdAndCollectedInFlow(userId, client, consentedScopes)
        } returns listOf(collectedClaim1)

        val result = manager.findBySession(session)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `areAllIdentifierAndRequiredClaimsCollectedInFlow - A required claim of the audience must be collected`() =
        runTest {
        val scope = "scope1"
        val required = claimWithConsentScope(scope, audienceId = "storefront", required = true)

        every { claimManager.listIdentifierClaims() } returns emptyList()
        every { claimManager.listRequiredClaims() } returns listOf(required)

        assertFalse(
            manager.areAllIdentifierAndRequiredClaimsCollectedInFlow(emptyList(), storefrontClient, listOf(scope))
        )
    }

    @Test
    fun `areAllIdentifierAndRequiredClaimsCollectedInFlow - A required claim no client may read reads as collected`() =
        runTest {
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
            every { value } returns "note"
        }

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { collectedClaimManager.findByUserId(userId) } returns listOf(collected)
        every { claimManager.listIdentifierClaims() } returns emptyList()
        every { claimManager.listRequiredClaims() } returns listOf(required)

        val readBack = manager.findByUserIdAndCollectedInFlow(userId, storefrontClient, listOf(scope))

        assertTrue(
            manager.areAllIdentifierAndRequiredClaimsCollectedInFlow(readBack, storefrontClient, listOf(scope))
        )
    }

    @Test
    fun `areAllIdentifierAndRequiredClaimsCollectedInFlow - An identifier claim is held to whatever its file says`() =
        runTest {
            // Neither required, nor collected in the flow, nor under a consented scope: an identifier claim
            // is held to by being what the account signs in with.
            val identifier = claimWithConsentScope(
                "scope1",
                required = false,
                collectedInFlow = false
            )

            every { claimManager.listIdentifierClaims() } returns listOf(identifier)
            every { claimManager.listRequiredClaims() } returns emptyList()

            assertFalse(
                manager.areAllIdentifierAndRequiredClaimsCollectedInFlow(emptyList(), storefrontClient, emptyList())
            )
        }

    @Test
    fun `areAllIdentifierAndRequiredClaimsCollectedInFlow - An identifier claim the sign-up collected is collected`() =
        runTest {
            val identifier = claimWithConsentScope(
                "scope1",
                required = false,
                collectedInFlow = false
            )
            val collected = mockk<CollectedClaim> {
                every { claim } returns identifier
                every { value } returns "ada@example.com"
            }

            every { claimManager.listIdentifierClaims() } returns listOf(identifier)
            every { claimManager.listRequiredClaims() } returns emptyList()

            assertTrue(
                manager.areAllIdentifierAndRequiredClaimsCollectedInFlow(
                    listOf(collected), storefrontClient, emptyList()
                )
            )
        }

    @Test
    fun `areAllIdentifierAndRequiredClaimsCollectedInFlow - A claim submitted blank is not collected`() =
        runTest {
            // A blank submission writes a row holding null rather than none, so a gate reading the row
            // would be satisfied by a person posting an empty field.
            val scope = "scope1"
            val required = claimWithConsentScope(scope, audienceId = "storefront", required = true)
            val cleared = mockk<CollectedClaim> {
                every { value } returns null
            }
            val answered = mockk<CollectedClaim> {
                every { claim } returns claimWithConsentScope("scope2")
                every { value } returns "something else"
            }

            every { claimManager.listIdentifierClaims() } returns emptyList()
            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertFalse(
                manager.areAllIdentifierAndRequiredClaimsCollectedInFlow(
                    listOf(cleared, answered), storefrontClient, listOf(scope)
                )
            )
        }

    @Test
    fun `areAllIdentifierAndRequiredClaimsCollectedInFlow - A required claim of another audience is not held to`() =
        runTest {
            val scope = "scope1"
            val required = claimWithConsentScope(scope, audienceId = "billing", required = true)

            every { claimManager.listIdentifierClaims() } returns emptyList()
            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertTrue(
                manager.areAllIdentifierAndRequiredClaimsCollectedInFlow(emptyList(), storefrontClient, listOf(scope))
            )
        }

    @Test
    fun `areAllIdentifierAndRequiredClaimsCollectedInFlow - A required claim of no audience is every audience's`() =
        runTest {
            val scope = "scope1"
            val required = claimWithConsentScope(scope, required = true)

            every { claimManager.listIdentifierClaims() } returns emptyList()
            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertFalse(
                manager.areAllIdentifierAndRequiredClaimsCollectedInFlow(emptyList(), storefrontClient, listOf(scope))
            )
        }

    @Test
    fun `areAllIdentifierAndRequiredClaimsCollectedInFlow - A required claim outside the consented scopes`() =
        runTest {
            val required = claimWithConsentScope("scope1", audienceId = "storefront", required = true)

            every { claimManager.listIdentifierClaims() } returns emptyList()
            every { claimManager.listRequiredClaims() } returns listOf(required)

            assertTrue(
                manager.areAllIdentifierAndRequiredClaimsCollectedInFlow(
                    emptyList(), storefrontClient, listOf("scope2")
                )
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
            consentAwareClaimManager.listClaimsCollectedInFlowWithScopes(client, consentedScopes)
        } returns listOf(claim1)
        coEvery { collectedClaimManager.applyUpdates(user, listOf(update1)) } returns listOf(collectedClaim1)

        val result = manager.updateInFlow(user, client, listOf(update1, update2), consentedScopes)

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

        val result = manager.updateByClient(user, audience, listOf(update1, update2), doors(consentedScopes))

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

        val result = manager.updateByClient(user, storefront, listOf(update), doors(listOf(scope)))

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

        val result = manager.updateByClient(user, audience, listOf(update), doors(listOf(scope)))

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

        val result = manager.updateByClient(user, storefront, listOf(update), doors(listOf(scope)))

        assertEquals(1, result.count())
        assertSame(collectedShared, result[0])
    }

    @Test
    fun `updateByPerson - Write through the person's door and not the flow's`() = runTest {
        val scope = "scope1"
        val writableClaim = claimWithConsentScope(scope, writableByPerson = true)
        // Collected by the flow on the same consent, which is no permission to write it with a token.
        val collectedOnlyClaim = PersonalClaim(
            id = "collected_only",
            verifiedId = null,
            dataType = ClaimDataType.STRING,
            group = null,
            required = false,
            allowedValues = null,
            publishedIn = ClaimPublicationPlace.entries.toSet(),
            publishedInWhenRequested = emptySet(),
            acl = claimWithConsentScope(scope).acl
        )
        val writableUpdate = mockk<CollectedClaimUpdate> {
            every { claim } returns writableClaim
        }
        val collectedOnlyUpdate = mockk<CollectedClaimUpdate> {
            every { claim } returns collectedOnlyClaim
        }

        val user = mockk<User>()
        val collectedWritable = mockk<CollectedClaim> {
            every { claim } returns writableClaim
        }

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery {
            collectedClaimManager.applyUpdates(user, listOf(writableUpdate))
        } returns listOf(collectedWritable)

        val result = manager.updateByPerson(
            user, audience.id, listOf(writableUpdate, collectedOnlyUpdate), doors(listOf(scope))
        )

        assertEquals(listOf(collectedWritable), result)
    }

    @Test
    fun `updateByPerson - Never write an identifier claim`() = runTest {
        val scope = "scope1"
        val emailClaim = claimWithConsentScope(scope, writableByPerson = true)
        val update = mockk<CollectedClaimUpdate> {
            every { claim } returns emailClaim
        }

        val user = mockk<User>()

        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
        // applyUpdates is stubbed for the empty list alone: reaching the assertion is proof the refused
        // update never travelled to the write.
        coEvery { collectedClaimManager.applyUpdates(user, emptyList()) } returns emptyList()

        val result = manager.updateByPerson(user, audience.id, listOf(update), doors(listOf(scope)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `updateByPerson - Answer what the person may read, not what the client may`() = runTest {
        val scope = "scope1"
        val claim = claimWithConsentScope(scope, writableByPerson = true, readableByClient = false)
        val update = mockk<CollectedClaimUpdate> {
            every { this@mockk.claim } returns claim
        }

        val user = mockk<User>()
        val collected = mockk<CollectedClaim> {
            every { this@mockk.claim } returns claim
        }

        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { collectedClaimManager.applyUpdates(user, listOf(update)) } returns listOf(collected)

        val result = manager.updateByPerson(user, audience.id, listOf(update), doors(listOf(scope)))

        assertEquals(listOf(collected), result)
    }
}
