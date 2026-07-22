package com.sympauthy.business.manager.reauth

import com.sympauthy.business.mapper.ReAuthenticationAttemptMapper
import com.sympauthy.business.model.reauth.PassedReAuthenticationAttempt
import com.sympauthy.business.model.reauth.PendingReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationMethod
import com.sympauthy.business.model.reauth.ReAuthenticationPurpose
import com.sympauthy.config.model.AuthorizationCodeConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.data.model.ReAuthenticationAttemptEntity
import com.sympauthy.data.repository.ReAuthenticationAttemptRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ReAuthenticationManagerTest {

    @MockK
    lateinit var reAuthenticationAttemptRepository: ReAuthenticationAttemptRepository

    @MockK
    lateinit var reAuthenticationAttemptMapper: ReAuthenticationAttemptMapper

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    @InjectMockKs
    lateinit var manager: ReAuthenticationManager

    private fun pending(
        id: UUID = UUID.randomUUID(),
        targetUserId: UUID = UUID.randomUUID(),
        purpose: ReAuthenticationPurpose = ReAuthenticationPurpose.PROVIDER_ATTACH,
        expirationDate: LocalDateTime = LocalDateTime.now().plusMinutes(30)
    ) = PendingReAuthenticationAttempt(
        id = id,
        targetUserId = targetUserId,
        purpose = purpose,
        expirationDate = expirationDate,
        attemptDate = LocalDateTime.now()
    )

    @Test
    fun `start - persists a pending attempt for the target user and purpose`() = runTest {
        val targetUserId = UUID.randomUUID()
        val entitySlot = slot<ReAuthenticationAttemptEntity>()
        val sentinel = pending(targetUserId = targetUserId)

        every { uncheckedAuthConfig.authorizationCode } returns AuthorizationCodeConfig(Duration.ofMinutes(30))
        coEvery { reAuthenticationAttemptRepository.save(capture(entitySlot)) } answers { entitySlot.captured }
        every { reAuthenticationAttemptMapper.toPendingReAuthenticationAttempt(any()) } returns sentinel

        val result = manager.start(targetUserId, ReAuthenticationPurpose.PROVIDER_ATTACH)

        assertSame(sentinel, result)
        val savedEntity = entitySlot.captured
        assertEquals(targetUserId, savedEntity.targetUserId)
        assertEquals(ReAuthenticationPurpose.PROVIDER_ATTACH.name, savedEntity.purpose)
        assertNull(savedEntity.passedDate)
        assertNull(savedEntity.passedMethod)
        assertEquals(savedEntity.attemptDate.plusMinutes(30), savedEntity.expirationDate)
    }

    @Test
    fun `getPendingOrNull - returns pending when valid`() = runTest {
        val id = UUID.randomUUID()
        val attempt = pending(id = id)
        val entity = mockk<ReAuthenticationAttemptEntity>()

        coEvery { reAuthenticationAttemptRepository.findById(id) } returns entity
        every { reAuthenticationAttemptMapper.toReAuthenticationAttempt(entity) } returns attempt

        val result = manager.getPendingOrNull(id, ReAuthenticationPurpose.PROVIDER_ATTACH)

        assertSame(attempt, result)
    }

    @Test
    fun `getPendingOrNull - returns null when not found`() = runTest {
        val id = UUID.randomUUID()
        coEvery { reAuthenticationAttemptRepository.findById(id) } returns null

        val result = manager.getPendingOrNull(id, ReAuthenticationPurpose.PROVIDER_ATTACH)

        assertNull(result)
    }

    @Test
    fun `getPendingOrNull - returns null when already passed`() = runTest {
        val id = UUID.randomUUID()
        val entity = mockk<ReAuthenticationAttemptEntity>()
        val passed = PassedReAuthenticationAttempt(
            id = id,
            targetUserId = UUID.randomUUID(),
            purpose = ReAuthenticationPurpose.PROVIDER_ATTACH,
            expirationDate = LocalDateTime.now().plusMinutes(30),
            attemptDate = LocalDateTime.now(),
            passedDate = LocalDateTime.now(),
            passedMethod = ReAuthenticationMethod.PASSWORD
        )

        coEvery { reAuthenticationAttemptRepository.findById(id) } returns entity
        every { reAuthenticationAttemptMapper.toReAuthenticationAttempt(entity) } returns passed

        val result = manager.getPendingOrNull(id, ReAuthenticationPurpose.PROVIDER_ATTACH)

        assertNull(result)
    }

    @Test
    fun `getPendingOrNull - returns null when expired`() = runTest {
        val id = UUID.randomUUID()
        val entity = mockk<ReAuthenticationAttemptEntity>()
        val expired = pending(id = id, expirationDate = LocalDateTime.now().minusMinutes(1))

        coEvery { reAuthenticationAttemptRepository.findById(id) } returns entity
        every { reAuthenticationAttemptMapper.toReAuthenticationAttempt(entity) } returns expired

        val result = manager.getPendingOrNull(id, ReAuthenticationPurpose.PROVIDER_ATTACH)

        assertNull(result)
    }

    @Test
    fun `markPassed - persists passed date and method then returns passed attempt`() = runTest {
        val attempt = pending()
        coJustRun {
            reAuthenticationAttemptRepository.updatePassedDatePassedMethod(attempt.id, any(), ReAuthenticationMethod.PASSWORD.name)
        }

        val result = manager.markPassed(attempt, ReAuthenticationMethod.PASSWORD)

        assertEquals(attempt.id, result.id)
        assertEquals(attempt.targetUserId, result.targetUserId)
        assertEquals(attempt.purpose, result.purpose)
        assertEquals(ReAuthenticationMethod.PASSWORD, result.passedMethod)
    }
}
