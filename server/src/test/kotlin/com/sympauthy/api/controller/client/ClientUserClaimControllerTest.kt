package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.client.ClientUserClaimResourceMapper
import com.sympauthy.api.resource.client.ClientUserClaimResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.ClientUserManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.user.ClientUser
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.security.ClientAuthentication
import io.mockk.coEvery
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
import java.util.*

@ExtendWith(MockKExtension::class)
class ClientUserClaimControllerTest {

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var clientUserManager: ClientUserManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var generatedClaimsManager: GeneratedClaimsManager

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var collectedClaimUpdateMapper: CollectedClaimUpdateMapper

    @MockK
    lateinit var claimMapper: ClientUserClaimResourceMapper

    @InjectMockKs
    lateinit var controller: ClientUserClaimController

    private val userId: UUID = UUID.randomUUID()

    private val user = mockk<User>()

    private val clientUser = mockk<ClientUser>()

    private val consent = mockk<Consent>()

    @Test
    fun `updateUserClaims - Refuse an identifier claim the client holds the write scope for`() = runTest {
        stubResolvedUser()
        every { claimManager.listIdentifierClaims() } returns listOf(identifierClaim())

        // The ACL pass and the write are left unstubbed: reaching the assertion is proof the identifier was
        // refused before either of them, whatever the deployment granted over the claim.
        val exception = assertThrows<BusinessException> {
            controller.updateUserClaims(authentication(), userId, mapOf(EMAIL_CLAIM to TAKEN_EMAIL))
        }

        assertEquals("client.identifier_claim", exception.detailsId)
        assertEquals(EMAIL_CLAIM, exception.values["claim"])
    }

    @Test
    fun `updateUserClaims - Refuse an identifier claim before a claim the client may not write`() = runTest {
        stubResolvedUser()
        every { claimManager.listIdentifierClaims() } returns listOf(identifierClaim())

        val exception = assertThrows<BusinessException> {
            controller.updateUserClaims(
                authentication(),
                userId,
                mapOf(UNKNOWN_CLAIM to "whatever", EMAIL_CLAIM to TAKEN_EMAIL)
            )
        }

        assertEquals("client.identifier_claim", exception.detailsId)
        assertEquals(EMAIL_CLAIM, exception.values["claim"])
    }

    @Test
    fun `updateUserClaims - Refuse a claim the client may not write`() = runTest {
        stubResolvedUser()
        every { claimManager.listIdentifierClaims() } returns emptyList()
        every { claimManager.findByIdOrNull(UNKNOWN_CLAIM) } returns null

        val exception = assertThrows<BusinessException> {
            controller.updateUserClaims(authentication(), userId, mapOf(UNKNOWN_CLAIM to "whatever"))
        }

        assertEquals("client.invalid_claim", exception.detailsId)
        assertEquals(UNKNOWN_CLAIM, exception.values["claim"])
    }

    @Test
    fun `updateUserClaims - Write a claim that is not an identifier`() = runTest {
        val regionClaim = mockk<Claim> {
            every { belongsToAudience(AUDIENCE) } returns true
            every { canBeWrittenByClient(listOf(CONSENTED_SCOPE), emptyList()) } returns true
        }
        val body = mapOf<String, Any?>(REGION_CLAIM to REGION)
        val updates = listOf(mockk<CollectedClaimUpdate>())
        val collectedClaims = listOf(mockk<CollectedClaim>())
        val generatedClaimValues = mapOf<String, Any?>("sub" to userId.toString())
        val resource = mockk<ClientUserClaimResource>()

        stubResolvedUser()
        every { clientUser.user } returns user
        every { consent.scopes } returns listOf(CONSENTED_SCOPE)
        every { claimManager.listIdentifierClaims() } returns emptyList()
        every { claimManager.findByIdOrNull(REGION_CLAIM) } returns regionClaim
        every { collectedClaimUpdateMapper.toUpdates(body) } returns updates
        coEvery {
            consentAwareCollectedClaimManager.updateByClient(
                user, AUDIENCE, updates, listOf(CONSENTED_SCOPE), emptyList()
            )
        } returns collectedClaims
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
                userId, AUDIENCE, listOf(CONSENTED_SCOPE), emptyList()
            )
        } returns collectedClaims
        coEvery { generatedClaimsManager.computeValues(userId) } returns generatedClaimValues
        every { claimMapper.toResource(userId, collectedClaims, generatedClaimValues) } returns resource

        val result = controller.updateUserClaims(authentication(), userId, body)

        assertSame(resource, result)
    }

    private fun authentication(): ClientAuthentication {
        val authenticationToken = mockk<AuthenticationToken> {
            every { clientId } returns CLIENT_ID
        }
        return ClientAuthentication(authenticationToken, emptyList())
    }

    private fun identifierClaim(): Claim = mockk {
        every { id } returns EMAIL_CLAIM
    }

    /**
     * The reads every call makes before it looks at the body: the client, its audience, the account, the
     * consent. What each of those records carries is stubbed by the tests that get far enough to read it.
     */
    private suspend fun stubResolvedUser() {
        val client = mockk<Client> {
            every { audience } returns mockk<Audience> { every { id } returns AUDIENCE }
        }

        coEvery { clientManager.findClientById(CLIENT_ID) } returns client
        coEvery { clientUserManager.findUserForAudienceOrNull(AUDIENCE, userId) } returns clientUser
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, AUDIENCE) } returns consent
    }

    private companion object {
        const val CLIENT_ID = "claim-write-app"
        const val AUDIENCE = "default"
        const val CONSENTED_SCOPE = "profile"
        const val EMAIL_CLAIM = "email"
        const val TAKEN_EMAIL = "somebody-else@example.com"
        const val REGION_CLAIM = "custom_region"
        const val REGION = "eu-west"
        const val UNKNOWN_CLAIM = "not_a_claim"
    }
}
