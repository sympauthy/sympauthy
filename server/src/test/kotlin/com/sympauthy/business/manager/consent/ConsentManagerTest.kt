package com.sympauthy.business.manager.consent

import com.sympauthy.business.mapper.ConsentMapper
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.data.model.ConsentEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.ConsentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class ConsentManagerTest {

    @MockK
    lateinit var consentRepository: ConsentRepository

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var consentMapper: ConsentMapper

    @InjectMockKs
    lateinit var consentManager: ConsentManager

    private val userId = UUID.randomUUID()
    private val audienceId = "test-audience"
    private val clientId = "test-client"
    private val scopes = listOf("read", "write")

    @Test
    fun `saveConsent - Creates new consent when none exists`() = runTest {
        val consent = mockk<Consent>()

        coEvery { consentRepository.findByUserIdAndAudienceIdAndRevokedAtIsNull(userId, audienceId) } returns null
        coEvery { consentRepository.save(any<ConsentEntity>()) } answers { firstArg() }
        every { consentMapper.toConsent(any()) } returns consent

        val result = consentManager.saveConsent(userId, audienceId, clientId, scopes)

        assertSame(consent, result)
        coVerify(exactly = 0) {
            consentRepository.updateRevokedAt(any(), any(), any(), any())
        }
    }

    @Test
    fun `saveConsent - Revokes existing consent and merges scopes`() = runTest {
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

        val result = consentManager.saveConsent(userId, audienceId, clientId, scopes)

        assertSame(consent, result)
        coVerify(exactly = 1) {
            consentRepository.updateRevokedAt(existingId, any(), "USER", userId)
        }
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
    fun `listActiveConsentsByAudience - Turns the page and its size into an offset`() = runTest {
        val entity = mockk<ConsentEntity>()
        val consent = mockk<Consent>()

        coEvery { consentRepository.findActiveByAudienceId(audienceId, 20, 60) } returns listOf(entity)
        every { consentMapper.toConsent(entity) } returns consent

        val result = consentManager.listActiveConsentsByAudience(audienceId, null, null, page = 3, size = 20)

        assertEquals(listOf(consent), result)
    }

    @Test
    fun `listActiveConsentsByAudience - Passes the provider and the subject to the filtered query`() = runTest {
        val entity = mockk<ConsentEntity>()
        val consent = mockk<Consent>()

        coEvery {
            consentRepository.findActiveByAudienceIdAndProvider(audienceId, "discord", "123", 20, 0)
        } returns listOf(entity)
        every { consentMapper.toConsent(entity) } returns consent

        val result = consentManager.listActiveConsentsByAudience(audienceId, "discord", "123", page = 0, size = 20)

        assertEquals(listOf(consent), result)
    }

    @Test
    fun `countActiveConsentsByAudience - Counts every consent of the audience when unfiltered`() = runTest {
        coEvery { consentRepository.countActiveByAudienceId(audienceId) } returns 42L

        assertEquals(42L, consentManager.countActiveConsentsByAudience(audienceId, null, null))
    }

    @Test
    fun `countActiveConsentsByAudience - Counts under the same filter as the page`() = runTest {
        coEvery {
            consentRepository.countActiveByAudienceIdAndProvider(audienceId, "discord", "123")
        } returns 7L

        assertEquals(7L, consentManager.countActiveConsentsByAudience(audienceId, "discord", "123"))
    }

    @Test
    fun `revokeConsent - Revokes consent and tokens`() = runTest {
        val consentId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val consent = Consent(
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

        coEvery {
            consentRepository.updateRevokedAt(consentId, any(), "ADMIN", adminId)
        } returns 1
        coEvery {
            tokenRepository.updateRevokedAtByUserIdAndClientId(
                userId, clientId, any(), "CONSENT_REVOKED", adminId
            )
        } returns 1

        consentManager.revokeConsent(consent, ConsentRevokedBy.ADMIN, adminId)

        coVerify(exactly = 1) {
            consentRepository.updateRevokedAt(consentId, any(), "ADMIN", adminId)
        }
        coVerify(exactly = 1) {
            tokenRepository.updateRevokedAtByUserIdAndClientId(
                userId, clientId, any(), "CONSENT_REVOKED", adminId
            )
        }
    }
}
