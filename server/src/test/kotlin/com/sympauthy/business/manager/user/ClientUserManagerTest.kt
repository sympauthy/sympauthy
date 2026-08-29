package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class ClientUserManagerTest {

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @InjectMockKs
    lateinit var manager: ClientUserManager

    private val audienceId = "test-audience"

    private fun mockUser(id: UUID = UUID.randomUUID()) = User(
        id = id,
        status = UserStatus.ENABLED,
        creationDate = LocalDateTime.now()
    )

    private fun mockConsent(userId: UUID, promptedByClientId: String = "test-client") = Consent(
        id = UUID.randomUUID(),
        userId = userId,
        audienceId = "test-audience",
        promptedByClientId = promptedByClientId,
        scopes = listOf("profile", "email"),
        consentedAt = LocalDateTime.now(),
        revokedAt = null,
        revokedBy = null,
        revokedById = null
    )

    private fun mockProviderUserInfo(userId: UUID, providerId: String, subject: String) = ProviderUserInfo(
        providerId = providerId,
        userId = userId,
        fetchDate = LocalDateTime.now(),
        changeDate = LocalDateTime.now(),
        userInfo = RawProviderClaims(subject = subject)
    )

    private fun mockCollectedClaim(userId: UUID, value: String) = CollectedClaim(
        userId = userId,
        claim = mockk<Claim>(),
        value = value,
        verified = true,
        collectionDate = LocalDateTime.now(),
        verificationDate = null
    )

    @Test
    fun `listUsersForAudience - Returns empty when no consents`() = runTest {
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 0L

        val (users, total) = manager.listUsersForAudience(audienceId, null, null, 0, 20)

        assertTrue(users.isEmpty())
        assertEquals(0, total)
        coVerify(exactly = 0) { consentManager.listActiveConsentsByAudience(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `listUsersForAudience - Returns users with active consents`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockUser(userId)
        val consent = mockConsent(userId)

        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 1L
        coEvery {
            consentManager.listActiveConsentsByAudience(audienceId, null, null, 0, 20)
        } returns listOf(consent)
        coEvery { userManager.listByIds(listOf(userId)) } returns listOf(user)
        coEvery { collectedClaimManager.listIdentifierByUserIds(listOf(userId)) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(listOf(userId)) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, null, null, 0, 20)

        assertEquals(1, users.size)
        assertEquals(userId, users[0].user.id)
        assertSame(consent, users[0].consent)
        assertEquals(1, total)
    }

    @Test
    fun `listUsersForAudience - Returns the users in the order of their consents`() = runTest {
        val userIds = (1..3).map { UUID.randomUUID() }
        val consents = userIds.map(::mockConsent)

        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 3L
        coEvery { consentManager.listActiveConsentsByAudience(audienceId, null, null, 0, 20) } returns consents
        // The batch read answers for a set of users and is free to return them in any order.
        coEvery { userManager.listByIds(userIds) } returns userIds.reversed().map(::mockUser)
        coEvery { collectedClaimManager.listIdentifierByUserIds(userIds) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(userIds) } returns emptyList()

        val (users, _) = manager.listUsersForAudience(audienceId, null, null, 0, 20)

        assertEquals(userIds, users.map { it.user.id })
    }

    @Test
    fun `listUsersForAudience - Attaches each user their own claims and providers`() = runTest {
        val userIds = (1..2).map { UUID.randomUUID() }
        val consents = userIds.map(::mockConsent)
        val claim = mockCollectedClaim(userIds[1], "email")
        val provider = mockProviderUserInfo(userIds[0], "discord", "123")

        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 2L
        coEvery { consentManager.listActiveConsentsByAudience(audienceId, null, null, 0, 20) } returns consents
        coEvery { userManager.listByIds(userIds) } returns userIds.map(::mockUser)
        coEvery { collectedClaimManager.listIdentifierByUserIds(userIds) } returns listOf(claim)
        coEvery { providerClaimsManager.listByUserIds(userIds) } returns listOf(provider)

        val (users, _) = manager.listUsersForAudience(audienceId, null, null, 0, 20)

        assertEquals(emptyList<CollectedClaim>(), users[0].identifierClaims)
        assertEquals(listOf(provider), users[0].providers)
        assertEquals(listOf(claim), users[1].identifierClaims)
        assertEquals(emptyList<ProviderUserInfo>(), users[1].providers)
    }

    @Test
    fun `listUsersForAudience - Filters by provider`() = runTest {
        val userId = UUID.randomUUID()
        val consent = mockConsent(userId)

        coEvery { consentManager.countActiveConsentsByAudience(audienceId, "discord", null) } returns 1L
        coEvery {
            consentManager.listActiveConsentsByAudience(audienceId, "discord", null, 0, 20)
        } returns listOf(consent)
        coEvery { userManager.listByIds(listOf(userId)) } returns listOf(mockUser(userId))
        coEvery { collectedClaimManager.listIdentifierByUserIds(listOf(userId)) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(listOf(userId)) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, "discord", null, 0, 20)

        assertEquals(listOf(userId), users.map { it.user.id })
        assertEquals(1, total)
    }

    @Test
    fun `listUsersForAudience - Filters by provider and subject`() = runTest {
        val userId = UUID.randomUUID()
        val consent = mockConsent(userId)

        coEvery { consentManager.countActiveConsentsByAudience(audienceId, "discord", "123") } returns 1L
        coEvery {
            consentManager.listActiveConsentsByAudience(audienceId, "discord", "123", 0, 20)
        } returns listOf(consent)
        coEvery { userManager.listByIds(listOf(userId)) } returns listOf(mockUser(userId))
        coEvery { collectedClaimManager.listIdentifierByUserIds(listOf(userId)) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(listOf(userId)) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, "discord", "123", 0, 20)

        assertEquals(listOf(userId), users.map { it.user.id })
        assertEquals(1, total)
    }

    @Test
    fun `listUsersForAudience - Paginates results`() = runTest {
        val pagedUserIds = (1..2).map { UUID.randomUUID() }
        val pagedConsents = pagedUserIds.map(::mockConsent)

        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 5L
        coEvery {
            consentManager.listActiveConsentsByAudience(audienceId, null, null, 1, 2)
        } returns pagedConsents
        coEvery { userManager.listByIds(pagedUserIds) } returns pagedUserIds.map(::mockUser)
        coEvery { collectedClaimManager.listIdentifierByUserIds(pagedUserIds) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(pagedUserIds) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, null, null, 1, 2)

        assertEquals(pagedUserIds, users.map { it.user.id })
        assertEquals(5, total)
    }

    @Test
    fun `listUsersForAudience - Returns the total when the page is past the last one`() = runTest {
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 5L
        coEvery { consentManager.listActiveConsentsByAudience(audienceId, null, null, 9, 20) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, null, null, 9, 20)

        assertTrue(users.isEmpty())
        assertEquals(5, total)
    }

    @Test
    fun `findUserForAudienceOrNull - Returns user with active consent`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockUser(userId)
        val consent = mockConsent(userId)

        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, audienceId) } returns consent
        coEvery { userManager.findByIdOrNull(userId) } returns user
        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns emptyList()
        coEvery { providerClaimsManager.findByUserId(userId) } returns emptyList()

        val result = manager.findUserForAudienceOrNull(audienceId, userId)

        assertNotNull(result)
        assertEquals(userId, result!!.user.id)
        assertSame(consent, result.consent)
    }

    @Test
    fun `findUserForAudienceOrNull - Returns null when no consent`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, audienceId) } returns null

        val result = manager.findUserForAudienceOrNull(audienceId, userId)

        assertNull(result)
    }

    @Test
    fun `findUserForAudienceOrNull - Returns null when user not found`() = runTest {
        val userId = UUID.randomUUID()
        val consent = mockConsent(userId)

        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, audienceId) } returns consent
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val result = manager.findUserForAudienceOrNull(audienceId, userId)

        assertNull(result)
    }
}
