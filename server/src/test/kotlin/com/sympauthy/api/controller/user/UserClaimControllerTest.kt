package com.sympauthy.api.controller.user

import com.sympauthy.api.exception.AccessTokenChallengeException
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.user.UserClaimResourceMapper
import com.sympauthy.api.resource.user.UserClaimResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InsufficientScope
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.PersonalClaim
import com.sympauthy.security.UserAuthentication
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
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class UserClaimControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var generatedClaimsManager: GeneratedClaimsManager

    @MockK
    lateinit var claimAclManager: ClaimAclManager

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var collectedClaimUpdateMapper: CollectedClaimUpdateMapper

    @MockK
    lateinit var claimMapper: UserClaimResourceMapper

    @InjectMockKs
    lateinit var controller: UserClaimController

    private val userId: UUID = UUID.randomUUID()

    private val user = User(
        id = userId,
        status = UserStatus.ENABLED,
        creationDate = LocalDateTime.of(2026, 6, 1, 12, 0, 0),
        sessionId = null
    )

    private val audience = Audience(id = AUDIENCE, tokenAudience = AUDIENCE)

    private val client = Client(
        id = "client",
        secret = null,
        audience = audience,
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
    )

    private val nameClaim = claim(NAME_CLAIM)

    private val nicknameClaim = claim(NICKNAME_CLAIM)

    @Test
    fun `getClaims - Return the claims the person may read`() = runTest {
        val doors = mockk<ClaimAclTokenDoors>()
        val collectedClaims = listOf(mockk<CollectedClaim>())
        val generatedClaimValues = mapOf<String, Any?>("sub" to userId.toString())
        val resource = mockk<UserClaimResource>()

        coEvery { userManager.findById(userId) } returns user
        every { claimAclManager.getTokenDoors(any(), any()) } returns doors
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByPerson(userId, AUDIENCE, doors)
        } returns collectedClaims
        coEvery { generatedClaimsManager.computeValuesReadableByPerson(user, doors) } returns generatedClaimValues
        every { claimMapper.toResource(collectedClaims, generatedClaimValues) } returns resource

        val result = controller.getClaims(authentication())

        assertSame(resource, result)
    }

    @Test
    fun `updateClaims - Refuse an identifier claim before a claim that does not exist`() = runTest {
        coEvery { userManager.findById(userId) } returns user
        every { claimManager.listIdentifierClaims() } returns listOf(claim(EMAIL_CLAIM))

        // The ACL pass and the write are left unstubbed: reaching the assertion is proof the identifier was
        // refused before either of them, whatever the deployment opened over the claim.
        val exception = assertThrows<BusinessException> {
            controller.updateClaims(
                authentication(reachesTheAcl = false),
                mapOf(UNKNOWN_CLAIM to "whatever", EMAIL_CLAIM to EMAIL)
            )
        }

        assertEquals("user.identifier_claim", exception.detailsId)
        assertEquals(EMAIL_CLAIM, exception.values["claim"])
    }

    @Test
    fun `updateClaims - Refuse a claim that does not exist`() = runTest {
        coEvery { userManager.findById(userId) } returns user
        every { claimManager.listIdentifierClaims() } returns emptyList()
        every { claimManager.findByIdOrNull(UNKNOWN_CLAIM) } returns null

        val exception = assertThrows<BusinessException> {
            controller.updateClaims(authentication(reachesTheAcl = false), mapOf(UNKNOWN_CLAIM to "whatever"))
        }

        assertEquals("user.invalid_claim", exception.detailsId)
        assertEquals(UNKNOWN_CLAIM, exception.values["claim"])
    }

    @Test
    fun `updateClaims - Refuse a claim of another audience rather than ignore it`() = runTest {
        val billingClaim = claim(TIER_CLAIM, audienceId = "billing")
        coEvery { userManager.findById(userId) } returns user
        every { claimManager.listIdentifierClaims() } returns emptyList()
        every { claimManager.findByIdOrNull(TIER_CLAIM) } returns billingClaim

        val exception = assertThrows<BusinessException> {
            controller.updateClaims(authentication(reachesTheAcl = false), mapOf(TIER_CLAIM to "gold"))
        }

        assertEquals("user.invalid_claim", exception.detailsId)
        assertEquals(TIER_CLAIM, exception.values["claim"])
    }

    @Test
    fun `updateClaims - Refuse by name a claim no token could be re-authorized into writing`() = runTest {
        val doors = mockk<ClaimAclTokenDoors> {
            every { canBeWrittenByPerson(nameClaim) } returns false
            every { canBeWrittenByPerson(nicknameClaim) } returns false
        }
        stubClaims(doors)
        every { claimAclManager.getPersonWriteChallenges(nameClaim, any(), any()) } returns
            listOf(InsufficientScope(listOf(WRITE_SCOPE)))
        every { claimAclManager.getPersonWriteChallenges(nicknameClaim, any(), any()) } returns emptyList()

        // A client re-authorizing for the name would be refused again for the nickname, so the nickname is
        // what it is told about.
        val exception = assertThrows<BusinessException> {
            controller.updateClaims(authentication(), mapOf(NAME_CLAIM to "Alice", NICKNAME_CLAIM to "Al"))
        }

        assertEquals("user.invalid_claim", exception.detailsId)
        assertEquals(NICKNAME_CLAIM, exception.values["claim"])
    }

    @Test
    fun `updateClaims - Challenge a claim the person's door refuses on a scope they did not consent to`() =
        runTest {
            val doors = mockk<ClaimAclTokenDoors> {
                every { canBeWrittenByPerson(nameClaim) } returns false
                every { canBeWrittenByPerson(nicknameClaim) } returns true
            }
            val challenges = listOf(InsufficientScope(listOf(WRITE_SCOPE)))
            stubClaims(doors)
            every { claimAclManager.getPersonWriteChallenges(nameClaim, any(), any()) } returns challenges
            every { claimAclManager.getPersonWriteChallenges(listOf(nameClaim), any(), any()) } returns challenges

            // The write is left unstubbed: reaching the assertion is proof nothing was written, the nickname the
            // door let through included.
            val exception = assertThrows<AccessTokenChallengeException> {
                controller.updateClaims(authentication(), mapOf(NAME_CLAIM to "Alice", NICKNAME_CLAIM to "Al"))
            }

            assertEquals(HttpStatus.FORBIDDEN, exception.status)
            assertEquals(challenges, exception.challenges)
        }

    @Test
    fun `updateClaims - Write the claims the person's door lets through`() = runTest {
        val doors = mockk<ClaimAclTokenDoors> {
            every { canBeWrittenByPerson(nameClaim) } returns true
        }
        val body = mapOf<String, Any?>(NAME_CLAIM to "Alice")
        val updates = listOf(mockk<CollectedClaimUpdate>())
        val collectedClaims = listOf(mockk<CollectedClaim>())
        val generatedClaimValues = mapOf<String, Any?>("sub" to userId.toString())
        val resource = mockk<UserClaimResource>()

        stubClaims(doors, nameClaim)
        every { collectedClaimUpdateMapper.toUpdates(body) } returns updates
        coEvery {
            consentAwareCollectedClaimManager.updateByPerson(user, audience, updates, doors)
        } returns collectedClaims
        coEvery { generatedClaimsManager.computeValuesReadableByPerson(user, doors) } returns generatedClaimValues
        every { claimMapper.toResource(collectedClaims, generatedClaimValues) } returns resource

        val result = controller.updateClaims(authentication(), body)

        assertSame(resource, result)
    }

    /**
     * The person's authentication, through a bearer token issued to the [client] under a grant of the [user].
     * What the ACL reads off it is stubbed only where the call [reachesTheAcl].
     */
    private fun authentication(reachesTheAcl: Boolean = true): UserAuthentication {
        val grant = mockk<Grant> {
            every { userId } returns this@UserClaimControllerTest.userId
            if (reachesTheAcl) every { authenticationDate } returns null
        }
        val token = mockk<AuthenticationToken> {
            every { this@mockk.grant } returns grant
            if (reachesTheAcl) every { dpopJkt } returns null
        }
        return UserAuthentication(token, client, emptyList(), emptyList())
    }

    private fun claim(id: String, audienceId: String? = null): EnabledClaim = PersonalClaim(
        id = id,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        allowedValues = null,
        audienceId = audienceId,
        publishedIn = emptySet(),
        publishedInWhenRequested = emptySet(),
        acl = ClaimAcl.SHUT
    )

    /**
     * What every call reaching the ACL reads first: the account, no identifier claim, the [claims] the body
     * names by their ids, and the [doors] they are asked through.
     */
    private fun stubClaims(doors: ClaimAclTokenDoors, vararg claims: EnabledClaim = arrayOf(nameClaim, nicknameClaim)) {
        coEvery { userManager.findById(userId) } returns user
        every { claimManager.listIdentifierClaims() } returns emptyList()
        claims.forEach { claim -> every { claimManager.findByIdOrNull(claim.id) } returns claim }
        every { claimAclManager.getTokenDoors(any(), any()) } returns doors
    }

    private companion object {
        const val AUDIENCE = "default"
        const val EMAIL_CLAIM = "email"
        const val EMAIL = "somebody-else@example.com"
        const val NAME_CLAIM = "name"
        const val NICKNAME_CLAIM = "nickname"
        const val TIER_CLAIM = "custom_tier"
        const val UNKNOWN_CLAIM = "not_a_claim"
        const val WRITE_SCOPE = "profile:write"
    }
}
