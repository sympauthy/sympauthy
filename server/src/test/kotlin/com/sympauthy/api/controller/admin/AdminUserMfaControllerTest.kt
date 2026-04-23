package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminUserMfaMethodResourceMapper
import com.sympauthy.api.resource.admin.AdminUserMfaMethodResource
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.user.User
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminUserMfaControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var mfaMapper: AdminUserMfaMethodResourceMapper

    @InjectMockKs
    lateinit var controller: AdminUserMfaController

    private val userId: UUID = UUID.randomUUID()
    private val mfaId: UUID = UUID.randomUUID()
    private val confirmedDate: LocalDateTime = LocalDateTime.of(2026, 2, 10, 8, 45, 0)

    private fun mockEnrollment(
        id: UUID = mfaId,
        enrollmentUserId: UUID = userId
    ): TotpEnrollment = TotpEnrollment(
        id = id,
        userId = enrollmentUserId,
        secret = ByteArray(20),
        creationDate = confirmedDate,
        confirmedDate = confirmedDate
    )

    private fun mockResource(id: UUID = mfaId): AdminUserMfaMethodResource = AdminUserMfaMethodResource(
        mfaId = id,
        type = "totp",
        registeredAt = confirmedDate
    )

    @Test
    fun `listMfaMethods - Returns paginated list of confirmed enrollments`() = runTest {
        val enrollment = mockEnrollment()
        val resource = mockResource()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)
        every { mfaMapper.toResource(enrollment) } returns resource

        val result = controller.listMfaMethods(userId, null, null)

        assertEquals(1, result.mfaMethods.size)
        assertEquals(mfaId, result.mfaMethods[0].mfaId)
        assertEquals("totp", result.mfaMethods[0].type)
        assertEquals(0, result.page)
        assertEquals(20, result.size)
        assertEquals(1, result.total)
    }

    @Test
    fun `listMfaMethods - Returns 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.listMfaMethods(userId, null, null)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `listMfaMethods - Returns empty list when user has no MFA`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()

        val result = controller.listMfaMethods(userId, null, null)

        assertTrue(result.mfaMethods.isEmpty())
        assertEquals(0, result.total)
    }

    @Test
    fun `listMfaMethods - Respects pagination parameters`() = runTest {
        val enrollments = (0 until 3).map { mockEnrollment(id = UUID.randomUUID()) }
        val resources = enrollments.map { mockResource(id = it.id) }
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns enrollments
        enrollments.forEachIndexed { i, e -> every { mfaMapper.toResource(e) } returns resources[i] }

        val result = controller.listMfaMethods(userId, 1, 2)

        assertEquals(1, result.mfaMethods.size)
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(3, result.total)
    }

    @Test
    fun `revokeMfaMethod - Deletes enrollment and returns revoked response`() = runTest {
        val enrollment = mockEnrollment()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollmentOrNull(mfaId) } returns enrollment
        coEvery { totpManager.deleteEnrollment(enrollment) } returns Unit

        val result = controller.revokeMfaMethod(userId, mfaId)

        assertEquals(userId, result.userId)
        assertEquals(mfaId, result.mfaId)
        assertTrue(result.revoked)
        coVerify { totpManager.deleteEnrollment(enrollment) }
    }

    @Test
    fun `revokeMfaMethod - Returns 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.revokeMfaMethod(userId, mfaId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `revokeMfaMethod - Returns 404 when MFA method not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollmentOrNull(mfaId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.revokeMfaMethod(userId, mfaId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `revokeMfaMethod - Returns 404 when enrollment belongs to different user`() = runTest {
        val otherUserId = UUID.randomUUID()
        val enrollment = mockEnrollment(enrollmentUserId = otherUserId)
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollmentOrNull(mfaId) } returns enrollment

        val exception = assertThrows<LocalizedHttpException> {
            controller.revokeMfaMethod(userId, mfaId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}
