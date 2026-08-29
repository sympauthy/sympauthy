package com.sympauthy.business.manager.flow.confirm

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.mapper.InteractiveFlowSessionConfirmMapper
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.InteractiveFlowSessionConfirm
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionConfirmEntity
import com.sympauthy.data.repository.InteractiveFlowSessionConfirmRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionConfirmManagerTest {

    @MockK
    lateinit var confirmRepository: InteractiveFlowSessionConfirmRepository

    @MockK
    lateinit var confirmMapper: InteractiveFlowSessionConfirmMapper

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionConfirmManager

    private val sessionId = UUID.randomUUID()
    private val session = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
    private val model = mockk<InteractiveFlowSessionConfirm>()

    @Test
    fun `setConfirm - Saves a new unconfirmed record when none exists`() = runTest {
        coEvery { confirmRepository.findBySessionId(sessionId) } returns null
        coEvery { confirmRepository.save(any()) } returns mockk()
        every { confirmMapper.toInteractiveFlowSessionConfirm(any()) } returns model

        val result = manager.setConfirm(session, ConfirmActionType.ENROLL_MFA, "client-x")

        assertSame(model, result)
        coVerify {
            confirmRepository.save(
                match {
                    it.sessionId == sessionId &&
                        it.action == ConfirmActionType.ENROLL_MFA.name &&
                        it.clientId == "client-x" &&
                        it.confirmedDate == null
                }
            )
        }
        coVerify(exactly = 0) { confirmRepository.update(any()) }
    }

    @Test
    fun `setConfirm - Updates the record when one already exists`() = runTest {
        coEvery { confirmRepository.findBySessionId(sessionId) } returns mockk()
        coEvery { confirmRepository.update(any()) } returns mockk()
        every { confirmMapper.toInteractiveFlowSessionConfirm(any()) } returns model

        val result = manager.setConfirm(session, ConfirmActionType.ENROLL_MFA, null)

        assertSame(model, result)
        coVerify {
            confirmRepository.update(
                match { it.sessionId == sessionId && it.clientId == null && it.confirmedDate == null }
            )
        }
        coVerify(exactly = 0) { confirmRepository.save(any()) }
    }

    @Test
    fun `fetchConfirmOrNull - Maps the entity when present`() = runTest {
        val entity = mockk<InteractiveFlowSessionConfirmEntity>()
        coEvery { confirmRepository.findBySessionId(sessionId) } returns entity
        every { confirmMapper.toInteractiveFlowSessionConfirm(entity) } returns model

        assertSame(model, manager.fetchConfirmOrNull(session))
    }

    @Test
    fun `fetchConfirmOrNull - Returns null when absent`() = runTest {
        coEvery { confirmRepository.findBySessionId(sessionId) } returns null

        assertNull(manager.fetchConfirmOrNull(session))
    }

    @Test
    fun `markConfirmed - Sets the confirmed date and updates the record`() = runTest {
        val entity = InteractiveFlowSessionConfirmEntity(
            sessionId = sessionId,
            action = ConfirmActionType.ENROLL_MFA.name,
            clientId = "client-x",
            confirmedDate = null
        )
        coEvery { confirmRepository.findBySessionId(sessionId) } returns entity
        coEvery { confirmRepository.update(any()) } returns mockk()
        every { confirmMapper.toInteractiveFlowSessionConfirm(any()) } returns model

        val result = manager.markConfirmed(session)

        assertSame(model, result)
        coVerify {
            confirmRepository.update(
                match<InteractiveFlowSessionConfirmEntity> { it.sessionId == sessionId && it.confirmedDate != null }
            )
        }
    }

    @Test
    fun `markConfirmed - Throws when the session carries no confirm record`() = runTest {
        coEvery { confirmRepository.findBySessionId(sessionId) } returns null

        val exception = assertThrows<BusinessException> { manager.markConfirmed(session) }

        assertEquals("flow.confirm.missing_record", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { confirmRepository.update(any()) }
    }
}
