package com.sympauthy.business.manager.consent

import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.ConsentMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.ConsentEntity
import com.sympauthy.data.repository.ConsentRepository
import com.sympauthy.data.repository.ObjectLockRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ConsentManagerTest {

    @MockK
    lateinit var consentRepository: ConsentRepository

    @MockK
    lateinit var grantManager: GrantManager

    @MockK
    lateinit var consentMapper: ConsentMapper

    @MockK
    lateinit var userManager: UserManager

    private val objectLockRepository = mockk<ObjectLockRepository>(relaxed = true)

    /** The real manager over the stripe table, so a test can say which row was taken before what. */
    var lockManager: LockManager = LockManager(objectLockRepository, HeldStripes())

    @InjectMockKs
    lateinit var consentManager: ConsentManager

    private val userId = UUID.randomUUID()
    private val audienceId = "test-audience"
    private val clientId = "test-client"
    private val user = User(
        id = userId,
        status = UserStatus.ENABLED,
        creationDate = LocalDateTime.now(),
        sessionId = null
    )
    private val audience = Audience(id = audienceId, tokenAudience = audienceId)
    private val scopes = listOf("read", "write")
    private val consentId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()

    @Test
    fun `saveConsent - Creates new consent when none exists`() = runTest {
        val consent = mockk<Consent>()

        coJustRun { userManager.checkPromoted(user.id) }
        coEvery { consentRepository.findByUserIdAndAudienceIdAndRevokedAtIsNull(userId, audienceId) } returns null
        coEvery { consentRepository.save(any<ConsentEntity>()) } answers { firstArg() }
        every { consentMapper.toConsent(any()) } returns consent

        val result = consentManager.saveConsent(user, audience, clientId, scopes)

        assertSame(consent, result)
        coVerify(exactly = 0) {
            consentRepository.updateRevokedAt(any(), any(), any(), any())
        }
        coVerify { userManager.checkPromoted(user.id) }
    }

    @Test
    fun `saveConsent - Revokes existing consent and merges scopes`() = runTest {
        coJustRun { userManager.checkPromoted(user.id) }
        val existingId = UUID.randomUUID()
        val existingEntity = mockk<ConsentEntity> {
            every { id } returns existingId
            every { scopes } returns arrayOf("existing-scope")
        }
        val consent = mockk<Consent>()

        coEvery {
            consentRepository.findByUserIdAndAudienceIdAndRevokedAtIsNull(
                userId,
                audienceId
            )
        } returns existingEntity
        coEvery {
            consentRepository.updateRevokedAt(existingId, any(), "USER", userId)
        } returns 1
        coEvery { consentRepository.save(any<ConsentEntity>()) } answers { firstArg() }
        every { consentMapper.toConsent(any()) } returns consent

        val result = consentManager.saveConsent(user, audience, clientId, scopes)

        assertSame(consent, result)
        coVerify(exactly = 1) {
            consentRepository.updateRevokedAt(existingId, any(), "USER", userId)
        }
    }

    @Test
    fun `saveConsent - Takes no scope from a consent revoked since it was read`() = runTest {
        coJustRun { userManager.checkPromoted(user.id) }
        val existingId = UUID.randomUUID()
        val existingEntity = mockk<ConsentEntity> { every { id } returns existingId }
        val saved = slot<ConsentEntity>()

        coEvery {
            consentRepository.findByUserIdAndAudienceIdAndRevokedAtIsNull(userId, audienceId)
        } returns existingEntity
        coEvery { consentRepository.updateRevokedAt(existingId, any(), "USER", userId) } returns 0
        coEvery { consentRepository.save(capture(saved)) } answers { firstArg() }
        every { consentMapper.toConsent(any()) } returns mockk()

        consentManager.saveConsent(user, audience, clientId, scopes)

        assertEquals(scopes, saved.captured.scopes.toList())
    }

    @Test
    fun `findActiveConsentsByUser - Returns mapped consents`() = runTest {
        val entity1 = mockk<ConsentEntity>()
        val entity2 = mockk<ConsentEntity>()
        val consent1 = mockk<Consent>()
        val consent2 = mockk<Consent>()

        coEvery { consentRepository.findByUserIdAndRevokedAtIsNull(userId) } returns listOf(entity1, entity2)
        every { consentMapper.toConsent(entity1) } returns consent1
        every { consentMapper.toConsent(entity2) } returns consent2

        val result = consentManager.findActiveConsentsByUser(userId)

        assertEquals(2, result.size)
        assertSame(consent1, result[0])
        assertSame(consent2, result[1])
    }

    @Test
    fun `revokeConsent - Revokes the grants of the audience at the moment it revokes the consent`() = runTest {
        val consentRevokedAt = slot<LocalDateTime>()
        val grantsRevokedAt = slot<LocalDateTime>()
        coEvery {
            consentRepository.updateRevokedAt(consentId, capture(consentRevokedAt), "ADMIN", adminId)
        } returns 1
        coEvery {
            grantManager.revokeGrantsByUserAndAudience(
                userId, audienceId, capture(grantsRevokedAt), GrantRevokedBy.CONSENT_REVOKED, adminId
            )
        } returns 2

        consentManager.revokeConsent(consent(), ConsentRevokedBy.ADMIN, adminId)

        assertEquals(consentRevokedAt.captured, grantsRevokedAt.captured)
        coVerifyOrder {
            objectLockRepository.lock(LockKey.User(userId).stripe)
            consentRepository.updateRevokedAt(consentId, any(), "ADMIN", adminId)
            grantManager.revokeGrantsByUserAndAudience(
                userId, audienceId, any(), GrantRevokedBy.CONSENT_REVOKED, adminId
            )
        }
    }

    @Test
    fun `revokeConsent - Revokes no grant where updating the consent reaches no row`() = runTest {
        coEvery { consentRepository.updateRevokedAt(consentId, any(), "ADMIN", adminId) } returns 0

        consentManager.revokeConsent(consent(), ConsentRevokedBy.ADMIN, adminId)

        coVerify(exactly = 0) {
            grantManager.revokeGrantsByUserAndAudience(any(), any(), any(), any(), any())
        }
    }

    private fun consent() = Consent(
        id = consentId,
        userId = userId,
        audienceId = audienceId,
        promptedByClientId = clientId,
        scopes = scopes,
        consentedAt = LocalDateTime.now(),
        revokedAt = null,
        revokedBy = null,
        revokedById = null
    )
}
