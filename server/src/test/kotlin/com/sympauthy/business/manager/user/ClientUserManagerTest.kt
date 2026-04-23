package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import com.sympauthy.data.repository.ProviderUserInfoRepository
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
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
    lateinit var providerUserInfoRepository: ProviderUserInfoRepository

    @InjectMockKs
    lateinit var manager: ClientUserManager

    private val audienceId = "test-audience"

    private fun mockUser(id: UUID = UUID.randomUUID()) = User(
        id = id,
        status = UserStatus.ENABLED,
        creationDate = LocalDateTime.now()
    )

    private fun mockConsent(userId: UUID, clientId: String = "test-client") = Consent(
        id = UUID.randomUUID(),
        userId = userId,
        audienceId = "test-audience",
        clientId = clientId,
        scopes = listOf("profile", "email"),
        consentedAt = LocalDateTime.now(),
        revokedAt = null,
        revokedBy = null,
        revokedById = null
    )

    private fun mockProviderEntity(userId: UUID, providerId: String, subject: String) = ProviderUserInfoEntity(
        id = ProviderUserInfoEntityId(providerId = providerId, userId = userId),
        fetchDate = LocalDateTime.now(),
        changeDate = LocalDateTime.now(),
        subject = subject
    )

    @Test
    fun `listUsersForAudience - Returns empty when no consents`() = runTest {
        coEvery { consentManager.findActiveConsentsByAudience(audienceId) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, null, null, 0, 20)

        assertTrue(users.isEmpty())
        assertEquals(0, total)
    }

    @Test
    fun `listUsersForAudience - Returns users with active consents`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockUser(userId)
        val consent = mockConsent(userId)

        coEvery { consentManager.findActiveConsentsByAudience(audienceId) } returns listOf(consent)
        coEvery { providerUserInfoRepository.findByUserIdInList(listOf(userId)) } returns emptyList()
        coEvery { userManager.findByIdOrNull(userId) } returns user
        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, null, null, 0, 20)

        assertEquals(1, users.size)
        assertEquals(userId, users[0].user.id)
        assertEquals(1, total)
    }

    @Test
    fun `listUsersForAudience - Filters by provider`() = runTest {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val user1 = mockUser(userId1)
        val consent1 = mockConsent(userId1)
        val consent2 = mockConsent(userId2)

        val provider = mockProviderEntity(userId1, "discord", "123")

        coEvery { consentManager.findActiveConsentsByAudience(audienceId) } returns listOf(consent1, consent2)
        coEvery { providerUserInfoRepository.findByUserIdInList(listOf(userId1, userId2)) } returns listOf(provider)
        coEvery { userManager.findByIdOrNull(userId1) } returns user1
        coEvery { collectedClaimManager.findIdentifierByUserId(userId1) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, "discord", null, 0, 20)

        assertEquals(1, users.size)
        assertEquals(userId1, users[0].user.id)
        assertEquals(1, total)
    }

    @Test
    fun `listUsersForAudience - Filters by provider and subject`() = runTest {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val consent1 = mockConsent(userId1)
        val consent2 = mockConsent(userId2)

        val provider1 = mockProviderEntity(userId1, "discord", "123")
        val provider2 = mockProviderEntity(userId2, "discord", "456")

        coEvery { consentManager.findActiveConsentsByAudience(audienceId) } returns listOf(consent1, consent2)
        coEvery { providerUserInfoRepository.findByUserIdInList(listOf(userId1, userId2)) } returns listOf(
            provider1,
            provider2
        )
        coEvery { userManager.findByIdOrNull(userId1) } returns mockUser(userId1)
        coEvery { collectedClaimManager.findIdentifierByUserId(userId1) } returns emptyList()

        val (users, total) = manager.listUsersForAudience(audienceId, "discord", "123", 0, 20)

        assertEquals(1, users.size)
        assertEquals(userId1, users[0].user.id)
        assertEquals(1, total)
    }

    @Test
    fun `listUsersForAudience - Paginates results`() = runTest {
        val userIds = (1..5).map { UUID.randomUUID() }
        val consents = userIds.map { mockConsent(it) }

        coEvery { consentManager.findActiveConsentsByAudience(audienceId) } returns consents
        coEvery { providerUserInfoRepository.findByUserIdInList(userIds) } returns emptyList()

        // Only mock the users on page 1 (indices 2, 3)
        userIds.forEachIndexed { index, id ->
            if (index in 2..3) {
                coEvery { userManager.findByIdOrNull(id) } returns mockUser(id)
                coEvery { collectedClaimManager.findIdentifierByUserId(id) } returns emptyList()
            }
        }

        val (users, total) = manager.listUsersForAudience(audienceId, null, null, 1, 2)

        assertEquals(2, users.size)
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
        coEvery { providerUserInfoRepository.findByUserId(userId) } returns emptyList()

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
