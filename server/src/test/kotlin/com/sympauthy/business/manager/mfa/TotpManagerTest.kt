package com.sympauthy.business.manager.mfa

import com.sympauthy.business.mapper.TotpEnrollmentMapper
import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.user.User
import com.sympauthy.data.model.TotpEnrollmentEntity
import com.sympauthy.data.repository.TotpEnrollmentRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@Suppress("unused")
@ExtendWith(MockKExtension::class)
class TotpManagerTest {

    @MockK
    lateinit var randomGenerator: RandomGenerator

    @MockK
    lateinit var totpEnrollmentRepository: TotpEnrollmentRepository

    @MockK
    lateinit var totpEnrollmentMapper: TotpEnrollmentMapper

    @InjectMockKs
    lateinit var manager: TotpManager

    companion object {
        // RFC 4226 Appendix D test secret: "12345678901234567890" as ASCII bytes
        val RFC_SECRET = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    }

    // --- generateCode ---

    @Test
    fun `generateCode - Returns correct code for RFC 4226 counter 0`() {
        assertEquals("755224", manager.generateCode(RFC_SECRET, 0))
    }

    @Test
    fun `generateCode - Returns correct code for RFC 4226 counter 1`() {
        assertEquals("287082", manager.generateCode(RFC_SECRET, 1))
    }

    @Test
    fun `generateCode - Returns correct code for RFC 4226 counter 2`() {
        assertEquals("359152", manager.generateCode(RFC_SECRET, 2))
    }

    @Test
    fun `generateCode - Always returns a 6-digit string`() {
        for (counter in 0L..9L) {
            val code = manager.generateCode(RFC_SECRET, counter)
            assertEquals(6, code.length, "Code for counter $counter should be 6 digits")
            assertTrue(code.all { it.isDigit() }, "Code for counter $counter should be numeric")
        }
    }

    // --- isCodeValid ---

    @Test
    fun `isCodeValid - Returns true for code at current step`() {
        val currentStep = System.currentTimeMillis() / 1000 / TotpManager.TIME_STEP_SECONDS
        assertTrue(manager.isCodeValid(RFC_SECRET, manager.generateCode(RFC_SECRET, currentStep)))
    }

    @Test
    fun `isCodeValid - Returns true for code at previous step within clock skew`() {
        val currentStep = System.currentTimeMillis() / 1000 / TotpManager.TIME_STEP_SECONDS
        assertTrue(manager.isCodeValid(RFC_SECRET, manager.generateCode(RFC_SECRET, currentStep - 1)))
    }

    @Test
    fun `isCodeValid - Returns true for code at next step within clock skew`() {
        val currentStep = System.currentTimeMillis() / 1000 / TotpManager.TIME_STEP_SECONDS
        assertTrue(manager.isCodeValid(RFC_SECRET, manager.generateCode(RFC_SECRET, currentStep + 1)))
    }

    @Test
    fun `isCodeValid - Returns false for code beyond clock skew`() {
        val currentStep = System.currentTimeMillis() / 1000 / TotpManager.TIME_STEP_SECONDS
        val tooOldStep = currentStep - (TotpManager.CLOCK_SKEW_STEPS + 1)
        assertFalse(manager.isCodeValid(RFC_SECRET, manager.generateCode(RFC_SECRET, tooOldStep)))
    }

    // --- encodeSecretToBase32 ---

    @Test
    fun `encodeSecretToBase32 - Encodes RFC 4226 test secret correctly`() {
        // The Base32 encoding of "12345678901234567890" is a well-known RFC test value
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", manager.encodeSecretToBase32(RFC_SECRET))
    }

    @Test
    fun `encodeSecretToBase32 - Pads output to a multiple of 8 characters`() {
        val encoded = manager.encodeSecretToBase32(byteArrayOf(0x00))
        assertEquals(0, encoded.length % 8)
        assertEquals("AA======", encoded)
    }

    // --- buildOtpauthUri ---

    @Test
    fun `buildOtpauthUri - Builds correctly formatted URI`() {
        val uri = manager.buildOtpauthUri("SympAuthy", "user@example.com", RFC_SECRET)
        assertTrue(uri.startsWith("otpauth://totp/"))
        assertTrue(uri.contains("secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
        assertTrue(uri.contains("issuer=SympAuthy"))
    }

    @Test
    fun `buildOtpauthUri - URL-encodes special characters in account`() {
        val uri = manager.buildOtpauthUri("SympAuthy", "user@example.com", RFC_SECRET)
        assertTrue(uri.contains("user%40example.com"))
    }

    // --- initiateEnrollment ---

    @Test
    fun `initiateEnrollment - Deletes pending enrollments before creating a new one`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockk<User> { every { id } returns userId }
        val pendingEntity = mockk<TotpEnrollmentEntity>()
        val newSecret = ByteArray(TotpManager.SECRET_LENGTH_IN_BYTES)
        val savedEntity = mockk<TotpEnrollmentEntity>()
        val enrollment = mockk<TotpEnrollment>()

        coEvery { totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNull(userId) } returns listOf(pendingEntity)
        coEvery { totpEnrollmentRepository.delete(pendingEntity) } returns 1
        every { randomGenerator.generate(TotpManager.SECRET_LENGTH_IN_BYTES) } returns newSecret
        coEvery { totpEnrollmentRepository.save(any()) } returns savedEntity
        every { totpEnrollmentMapper.toTotpEnrollment(savedEntity) } returns enrollment

        val result = manager.initiateEnrollment(user)

        coVerify { totpEnrollmentRepository.delete(pendingEntity) }
        assertSame(enrollment, result)
    }

    @Test
    fun `initiateEnrollment - Works when there are no pending enrollments`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockk<User> { every { id } returns userId }
        val newSecret = ByteArray(TotpManager.SECRET_LENGTH_IN_BYTES)
        val savedEntity = mockk<TotpEnrollmentEntity>()
        val enrollment = mockk<TotpEnrollment>()

        coEvery { totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNull(userId) } returns emptyList()
        every { randomGenerator.generate(TotpManager.SECRET_LENGTH_IN_BYTES) } returns newSecret
        coEvery { totpEnrollmentRepository.save(any()) } returns savedEntity
        every { totpEnrollmentMapper.toTotpEnrollment(savedEntity) } returns enrollment

        val result = manager.initiateEnrollment(user)

        coVerify(exactly = 0) { totpEnrollmentRepository.delete(any()) }
        assertSame(enrollment, result)
    }

    // --- confirmEnrollment ---

    @Test
    fun `confirmEnrollment - Returns null and does not update for invalid code`() = runTest {
        val currentStep = System.currentTimeMillis() / 1000 / TotpManager.TIME_STEP_SECONDS
        val invalidCode = manager.generateCode(RFC_SECRET, currentStep - 100)
        val enrollment = mockk<TotpEnrollment> { every { secret } returns RFC_SECRET }

        val result = manager.confirmEnrollment(enrollment, invalidCode)

        assertNull(result)
        coVerify(exactly = 0) { totpEnrollmentRepository.updateConfirmedDate(any(), any()) }
    }

    @Test
    fun `confirmEnrollment - Updates confirmedDate and returns enrollment for valid code`() = runTest {
        val enrollmentId = UUID.randomUUID()
        val currentStep = System.currentTimeMillis() / 1000 / TotpManager.TIME_STEP_SECONDS
        val validCode = manager.generateCode(RFC_SECRET, currentStep)
        val enrollment = mockk<TotpEnrollment> {
            every { id } returns enrollmentId
            every { secret } returns RFC_SECRET
        }
        val updatedEntity = mockk<TotpEnrollmentEntity>()
        val updatedEnrollment = mockk<TotpEnrollment>()

        coJustRun { totpEnrollmentRepository.updateConfirmedDate(enrollmentId, any()) }
        coEvery { totpEnrollmentRepository.findById(enrollmentId) } returns updatedEntity
        every { totpEnrollmentMapper.toTotpEnrollment(updatedEntity) } returns updatedEnrollment

        val result = manager.confirmEnrollment(enrollment, validCode)

        coVerify { totpEnrollmentRepository.updateConfirmedDate(enrollmentId, any()) }
        assertSame(updatedEnrollment, result)
    }

    // --- isCodeValidForUser ---

    @Test
    fun `isCodeValidForUser - Returns false when there are no confirmed enrollments`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNotNull(userId) } returns emptyList()

        assertFalse(manager.isCodeValidForUser(userId, "123456"))
    }

    @Test
    fun `isCodeValidForUser - Returns true when code matches a confirmed enrollment`() = runTest {
        val userId = UUID.randomUUID()
        val currentStep = System.currentTimeMillis() / 1000 / TotpManager.TIME_STEP_SECONDS
        val validCode = manager.generateCode(RFC_SECRET, currentStep)
        val entity = mockk<TotpEnrollmentEntity> { every { secret } returns RFC_SECRET }

        coEvery { totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNotNull(userId) } returns listOf(entity)

        assertTrue(manager.isCodeValidForUser(userId, validCode))
    }

    @Test
    fun `isCodeValidForUser - Returns false when code does not match any enrollment`() = runTest {
        val userId = UUID.randomUUID()
        val currentStep = System.currentTimeMillis() / 1000 / TotpManager.TIME_STEP_SECONDS
        val invalidCode = manager.generateCode(RFC_SECRET, currentStep - 100)
        val entity = mockk<TotpEnrollmentEntity> { every { secret } returns RFC_SECRET }

        coEvery { totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNotNull(userId) } returns listOf(entity)

        assertFalse(manager.isCodeValidForUser(userId, invalidCode))
    }

    // --- findConfirmedEnrollmentOrNull ---

    @Test
    fun `findConfirmedEnrollmentOrNull - Returns enrollment when found and confirmed`() = runTest {
        val enrollmentId = UUID.randomUUID()
        val entity = mockk<TotpEnrollmentEntity> {
            every { confirmedDate } returns java.time.LocalDateTime.now()
        }
        val enrollment = mockk<TotpEnrollment>()

        coEvery { totpEnrollmentRepository.findById(enrollmentId) } returns entity
        every { totpEnrollmentMapper.toTotpEnrollment(entity) } returns enrollment

        val result = manager.findConfirmedEnrollmentOrNull(enrollmentId)

        assertSame(enrollment, result)
    }

    @Test
    fun `findConfirmedEnrollmentOrNull - Returns null when not found`() = runTest {
        val enrollmentId = UUID.randomUUID()

        coEvery { totpEnrollmentRepository.findById(enrollmentId) } returns null

        assertNull(manager.findConfirmedEnrollmentOrNull(enrollmentId))
    }

    @Test
    fun `findConfirmedEnrollmentOrNull - Returns null when found but not confirmed`() = runTest {
        val enrollmentId = UUID.randomUUID()
        val entity = mockk<TotpEnrollmentEntity> {
            every { confirmedDate } returns null
        }

        coEvery { totpEnrollmentRepository.findById(enrollmentId) } returns entity

        assertNull(manager.findConfirmedEnrollmentOrNull(enrollmentId))
    }

    // --- deleteEnrollment ---

    @Test
    fun `deleteEnrollment - Deletes enrollment by id`() = runTest {
        val enrollmentId = UUID.randomUUID()
        val enrollment = mockk<TotpEnrollment> { every { id } returns enrollmentId }

        coEvery { totpEnrollmentRepository.deleteById(enrollmentId) } returns 1

        manager.deleteEnrollment(enrollment)

        coVerify { totpEnrollmentRepository.deleteById(enrollmentId) }
    }

    // --- findConfirmedEnrollments ---

    @Test
    fun `findConfirmedEnrollments - Returns mapped list of confirmed enrollments`() = runTest {
        val userId = UUID.randomUUID()
        val entity1 = mockk<TotpEnrollmentEntity>()
        val entity2 = mockk<TotpEnrollmentEntity>()
        val enrollment1 = mockk<TotpEnrollment>()
        val enrollment2 = mockk<TotpEnrollment>()

        coEvery { totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNotNull(userId) } returns listOf(entity1, entity2)
        every { totpEnrollmentMapper.toTotpEnrollment(entity1) } returns enrollment1
        every { totpEnrollmentMapper.toTotpEnrollment(entity2) } returns enrollment2

        val result = manager.findConfirmedEnrollments(userId)

        assertEquals(2, result.size)
        assertSame(enrollment1, result[0])
        assertSame(enrollment2, result[1])
    }
}
