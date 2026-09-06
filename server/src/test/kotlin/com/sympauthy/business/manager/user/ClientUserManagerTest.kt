package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import io.mockk.coEvery
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
        creationDate = LocalDateTime.now(),
        sessionId = null
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

    private fun mockCollectedClaim(userId: UUID) = CollectedClaim(
        userId = userId,
        claim = mockk<Claim>(),
        value = "user@test.com",
        verified = true,
        collectionDate = LocalDateTime.now(),
        verificationDate = LocalDateTime.now()
    )

    private fun mockProviderUserInfo(userId: UUID, providerId: String, subject: String) = ProviderUserInfo(
        providerId = providerId,
        userId = userId,
        linkDate = LocalDateTime.now(),
        fetchDate = LocalDateTime.now(),
        changeDate = LocalDateTime.now(),
        sessionId = null,
        userInfo = RawProviderClaims(subject = subject)
    )

    @Test
    fun `listUsersForAudience - Returns empty when no consents`() = runTest {
        coEvery { consentManager.listActiveConsentsByAudience(audienceId, null, null, 0, 20) } returns emptyList()
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 0

        val page = manager.listUsersForAudience(audienceId, null, null, PageParams(0, 20))

        assertTrue(page.items.isEmpty())
        assertEquals(0, page.total)
    }

    @Test
    fun `listUsersForAudience - Reports the total even on a page past the last`() = runTest {
        coEvery { consentManager.listActiveConsentsByAudience(audienceId, null, null, 5, 20) } returns emptyList()
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 42

        val page = manager.listUsersForAudience(audienceId, null, null, PageParams(5, 20))

        assertTrue(page.items.isEmpty())
        assertEquals(42, page.total)
    }

    @Test
    fun `listUsersForAudience - Returns users with active consents`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockUser(userId)
        val consent = mockConsent(userId)

        coEvery { consentManager.listActiveConsentsByAudience(audienceId, null, null, 0, 20) } returns listOf(consent)
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 1
        coEvery { userManager.listByIds(listOf(userId)) } returns listOf(user)
        coEvery { collectedClaimManager.listIdentifierByUserIds(listOf(userId)) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(listOf(userId)) } returns emptyList()

        val page = manager.listUsersForAudience(audienceId, null, null, PageParams(0, 20))

        assertEquals(1, page.items.size)
        assertEquals(userId, page.items[0].user.id)
        assertSame(consent, page.items[0].consent)
        assertEquals(1, page.total)
    }

    @Test
    fun `listUsersForAudience - Attaches each user their own claims and providers`() = runTest {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val consent1 = mockConsent(userId1)
        val consent2 = mockConsent(userId2)
        val claim1 = mockCollectedClaim(userId1)
        val provider2 = mockProviderUserInfo(userId2, "discord", "456")

        coEvery {
            consentManager.listActiveConsentsByAudience(audienceId, null, null, 0, 20)
        } returns listOf(consent1, consent2)
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 2
        coEvery { userManager.listByIds(listOf(userId1, userId2)) } returns listOf(
            mockUser(userId1),
            mockUser(userId2)
        )
        coEvery {
            collectedClaimManager.listIdentifierByUserIds(listOf(userId1, userId2))
        } returns listOf(claim1)
        coEvery { providerClaimsManager.listByUserIds(listOf(userId1, userId2)) } returns listOf(provider2)

        val page = manager.listUsersForAudience(audienceId, null, null, PageParams(0, 20))

        assertEquals(listOf(claim1), page.items[0].identifierClaims)
        assertTrue(page.items[0].providers.isEmpty())
        assertTrue(page.items[1].identifierClaims.isEmpty())
        assertEquals(listOf(provider2), page.items[1].providers)
    }

    /**
     * The batch read is deliberately answered in the reverse of the consent order: the page must follow the
     * order the consent query guarantees, not the one an IN-list happens to come back in.
     */
    @Test
    fun `listUsersForAudience - Follows the consent order rather than the user batch order`() = runTest {
        val userIds = (1..3).map { UUID.randomUUID() }
        val consents = userIds.map { mockConsent(it) }

        coEvery { consentManager.listActiveConsentsByAudience(audienceId, null, null, 0, 20) } returns consents
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 3
        coEvery { userManager.listByIds(userIds) } returns userIds.map(::mockUser).reversed()
        coEvery { collectedClaimManager.listIdentifierByUserIds(userIds) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(userIds) } returns emptyList()

        val page = manager.listUsersForAudience(audienceId, null, null, PageParams(0, 20))

        assertEquals(userIds, page.items.map { it.user.id })
    }

    @Test
    fun `listUsersForAudience - Drops a consent whose user has disappeared`() = runTest {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val consents = listOf(mockConsent(userId1), mockConsent(userId2))

        coEvery { consentManager.listActiveConsentsByAudience(audienceId, null, null, 0, 20) } returns consents
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, null, null) } returns 2
        coEvery { userManager.listByIds(listOf(userId1, userId2)) } returns listOf(mockUser(userId2))
        coEvery { collectedClaimManager.listIdentifierByUserIds(listOf(userId1, userId2)) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(listOf(userId1, userId2)) } returns emptyList()

        val page = manager.listUsersForAudience(audienceId, null, null, PageParams(0, 20))

        assertEquals(listOf(userId2), page.items.map { it.user.id })
    }

    @Test
    fun `listUsersForAudience - Passes the provider filter and the page down to the consent query`() = runTest {
        val userId = UUID.randomUUID()
        val consent = mockConsent(userId)
        val provider = mockProviderUserInfo(userId, "discord", "123")

        coEvery {
            consentManager.listActiveConsentsByAudience(audienceId, "discord", "123", 1, 2)
        } returns listOf(consent)
        coEvery { consentManager.countActiveConsentsByAudience(audienceId, "discord", "123") } returns 3
        coEvery { userManager.listByIds(listOf(userId)) } returns listOf(mockUser(userId))
        coEvery { collectedClaimManager.listIdentifierByUserIds(listOf(userId)) } returns emptyList()
        coEvery { providerClaimsManager.listByUserIds(listOf(userId)) } returns listOf(provider)

        val page = manager.listUsersForAudience(audienceId, "discord", "123", PageParams(1, 2))

        assertEquals(listOf(userId), page.items.map { it.user.id })
        assertEquals(3, page.total)
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
