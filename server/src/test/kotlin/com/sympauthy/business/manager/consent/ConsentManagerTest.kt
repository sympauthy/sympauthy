package com.sympauthy.business.manager.consent

import com.sympauthy.business.mapper.ConsentMapper
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.data.model.ConsentEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.ConsentRepository
import io.mockk.*
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
    private val clientId = "test-client"
    private val scopes = listOf("read", "write")

    @Test
    fun `saveGrantedConsent - Creates new consent when none exists`() = runTest {
        val consent = mockk<Consent>()

        coEvery { consentRepository.findByUserIdAndClientIdAndRevokedAtIsNull(userId, clientId) } returns null
        coEvery { consentRepository.save(any<ConsentEntity>()) } answers { firstArg() }
        every { consentMapper.toConsent(any()) } returns consent

        val result = consentManager.saveGrantedConsent(userId, clientId, scopes)

        assertSame(consent, result)
        coVerify(exactly = 0) {
            consentRepository.updateRevokedAt(any(), any(), any(), any())
        }
    }

    @Test
    fun `saveGrantedConsent - Revokes existing consent before creating new one`() = runTest {
        val existingId = UUID.randomUUID()
        val existingEntity = mockk<ConsentEntity> {
            every { id } returns existingId
        }
        val consent = mockk<Consent>()

        coEvery { consentRepository.findByUserIdAndClientIdAndRevokedAtIsNull(userId, clientId) } returns existingEntity
        coEvery {
            consentRepository.updateRevokedAt(existingId, any(), "USER", userId)
        } returns 1
        coEvery { consentRepository.save(any<ConsentEntity>()) } answers { firstArg() }
        every { consentMapper.toConsent(any()) } returns consent

        val result = consentManager.saveGrantedConsent(userId, clientId, scopes)

        assertSame(consent, result)
        coVerify(exactly = 1) {
            consentRepository.updateRevokedAt(existingId, any(), "USER", userId)
        }
    }

    @Test
    fun `findActiveConsentOrNull - Returns consent when found`() = runTest {
        val entity = mockk<ConsentEntity>()
        val consent = mockk<Consent>()

        coEvery { consentRepository.findByUserIdAndClientIdAndRevokedAtIsNull(userId, clientId) } returns entity
        every { consentMapper.toConsent(entity) } returns consent

        val result = consentManager.findActiveConsentOrNull(userId, clientId)

        assertSame(consent, result)
    }

    @Test
    fun `findActiveConsentOrNull - Returns null when not found`() = runTest {
        coEvery { consentRepository.findByUserIdAndClientIdAndRevokedAtIsNull(userId, clientId) } returns null

        val result = consentManager.findActiveConsentOrNull(userId, clientId)

        assertNull(result)
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
    fun `revokeConsent - Revokes consent and tokens`() = runTest {
        val consentId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val consent = Consent(
            id = consentId,
            userId = userId,
            clientId = clientId,
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
