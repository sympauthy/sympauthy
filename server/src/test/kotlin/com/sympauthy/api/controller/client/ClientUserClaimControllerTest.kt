package com.sympauthy.api.controller.client

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.client.ClientUserClaimResourceMapper
import com.sympauthy.api.resource.client.ClientUserClaimResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.ClaimAclManager
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
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.security.ClientAuthentication
import io.micronaut.http.HttpStatus
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
    lateinit var clientUserManager: ClientUserManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var generatedClaimsManager: GeneratedClaimsManager

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var claimAclManager: ClaimAclManager

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

    private val audience = mockk<Audience> { every { id } returns AUDIENCE }

    private val client = Client(
        id = "client",
        secret = null,
        audience = audience,
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
    )

    /** What the client [authentication] carries presents, acting on a person who consented to [CONSENTED_SCOPE]. */
    private val context = ClaimAclContext(
        client = client,
        consentedScopes = listOf(CONSENTED_SCOPE),
        grantedScopes = emptyList(),
        authenticationDate = null,
        bound = false
    )

    @Test
    fun `getUserClaims - Return the claims the client may read`() = runTest {
        val collectedClaims = listOf(mockk<CollectedClaim>())
        val generatedClaimValues = mapOf<String, Any?>("sub" to userId.toString())
        val resource = mockk<ClientUserClaimResource>()

        stubResolvedUser()
        every { clientUser.user } returns user
        every { consent.scopes } returns listOf(CONSENTED_SCOPE)
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(userId, AUDIENCE, context)
        } returns collectedClaims
        coEvery { generatedClaimsManager.computeValuesReadableByClient(user, context) } returns generatedClaimValues
        every { claimMapper.toResource(userId, collectedClaims, generatedClaimValues) } returns resource

        val result = controller.getUserClaims(authentication(bearerToken()), userId)

        assertSame(resource, result)
    }

    @Test
    fun `getUserClaims - Throw 404 when the user is not of the client audience`() = runTest {
        coEvery { clientUserManager.findUserForAudienceOrNull(AUDIENCE, userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.getUserClaims(authentication(), userId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `getUserClaims - Throw 404 when the user has not consented to the client audience`() = runTest {
        coEvery { clientUserManager.findUserForAudienceOrNull(AUDIENCE, userId) } returns clientUser
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, AUDIENCE) } returns null

        // The claim reads are left unstubbed: reaching the assertion is proof nothing was read without a consent.
        val exception = assertThrows<LocalizedHttpException> {
            controller.getUserClaims(authentication(), userId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `updateUserClaims - Refuse an identifier claim whatever its ACL opens to the client`() = runTest {
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
        every { consent.scopes } returns listOf(CONSENTED_SCOPE)
        every { claimAclManager.getTokenDoors(context, any()) } returns mockk()

        val exception = assertThrows<BusinessException> {
            controller.updateUserClaims(authentication(bearerToken()), userId, mapOf(UNKNOWN_CLAIM to "whatever"))
        }

        assertEquals("client.invalid_claim", exception.detailsId)
        assertEquals(UNKNOWN_CLAIM, exception.values["claim"])
    }

    @Test
    fun `updateUserClaims - Write a claim that is not an identifier`() = runTest {
        val regionClaim = mockk<EnabledClaim> {
            every { belongsToAudience(AUDIENCE) } returns true
        }
        every { claimAclManager.getTokenDoors(context, any()) } returns mockk {
            every { canBeWrittenByClient(regionClaim) } returns true
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
                user, audience, updates, context
            )
        } returns collectedClaims
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
                userId, AUDIENCE, context
            )
        } returns collectedClaims
        coEvery { generatedClaimsManager.computeValuesReadableByClient(user, context) } returns generatedClaimValues
        every { claimMapper.toResource(userId, collectedClaims, generatedClaimValues) } returns resource

        val result = controller.updateUserClaims(authentication(bearerToken()), userId, body)

        assertSame(resource, result)
    }

    /**
     * The client's authentication, presenting [token]: a test reaching the ACL passes [bearerToken], which is
     * what the context reads the binding off.
     */
    private fun authentication(token: AuthenticationToken = mockk()): ClientAuthentication =
        ClientAuthentication(token, client, emptyList())

    private fun bearerToken(): AuthenticationToken = mockk { every { dpopJkt } returns null }

    private fun identifierClaim(): EnabledClaim = mockk {
        every { id } returns EMAIL_CLAIM
    }

    /**
     * The reads every call makes before it looks at the body: the account and the consent, both under the
     * audience of the client [authentication] carries. What each of those records carries is stubbed by the
     * tests that get far enough to read it.
     */
    private suspend fun stubResolvedUser() {
        coEvery { clientUserManager.findUserForAudienceOrNull(AUDIENCE, userId) } returns clientUser
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, AUDIENCE) } returns consent
    }

    private companion object {
        const val AUDIENCE = "default"
        const val CONSENTED_SCOPE = "profile"
        const val EMAIL_CLAIM = "email"
        const val TAKEN_EMAIL = "somebody-else@example.com"
        const val REGION_CLAIM = "custom_region"
        const val REGION = "eu-west"
        const val UNKNOWN_CLAIM = "not_a_claim"
    }
}
