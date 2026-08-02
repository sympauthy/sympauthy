package com.sympauthy.business.manager.flow.reauth

import com.sympauthy.business.mapper.InteractiveFlowSessionReauthenticationMapper
import com.sympauthy.business.model.flow.InteractiveFlowSessionReauthentication
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionReauthenticationEntity
import com.sympauthy.data.repository.InteractiveFlowSessionReauthenticationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionReauthenticationManagerTest {

    @MockK
    lateinit var reauthenticationRepository: InteractiveFlowSessionReauthenticationRepository

    @MockK
    lateinit var reauthenticationMapper: InteractiveFlowSessionReauthenticationMapper

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionReauthenticationManager

    private val sessionId = UUID.randomUUID()
    private val session = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
    private val model = mockk<InteractiveFlowSessionReauthentication>()

    @Test
    fun `markPrimaryCredentialProven - Saves a new proven record when none exists`() = runTest {
        coEvery { reauthenticationRepository.findBySessionId(sessionId) } returns null
        coEvery { reauthenticationRepository.save(any()) } returns mockk()
        every { reauthenticationMapper.toInteractiveFlowSessionReauthentication(any()) } returns model

        val result = manager.markPrimaryCredentialProven(session)

        assertSame(model, result)
        coVerify {
            reauthenticationRepository.save(
                match { it.sessionId == sessionId && it.primaryCredentialProvenDate != null }
            )
        }
        coVerify(exactly = 0) { reauthenticationRepository.update(any()) }
    }

    @Test
    fun `markPrimaryCredentialProven - Updates the record when one already exists`() = runTest {
        coEvery { reauthenticationRepository.findBySessionId(sessionId) } returns mockk()
        coEvery { reauthenticationRepository.update(any()) } returns mockk()
        every { reauthenticationMapper.toInteractiveFlowSessionReauthentication(any()) } returns model

        val result = manager.markPrimaryCredentialProven(session)

        assertSame(model, result)
        coVerify {
            reauthenticationRepository.update(
                match<InteractiveFlowSessionReauthenticationEntity> {
                    it.sessionId == sessionId && it.primaryCredentialProvenDate != null
                }
            )
        }
        coVerify(exactly = 0) { reauthenticationRepository.save(any()) }
    }

    @Test
    fun `fetchReauthenticationOrNull - Maps the entity when present`() = runTest {
        val entity = mockk<InteractiveFlowSessionReauthenticationEntity>()
        coEvery { reauthenticationRepository.findBySessionId(sessionId) } returns entity
        every { reauthenticationMapper.toInteractiveFlowSessionReauthentication(entity) } returns model

        assertSame(model, manager.fetchReauthenticationOrNull(session))
    }

    @Test
    fun `fetchReauthenticationOrNull - Returns null when absent`() = runTest {
        coEvery { reauthenticationRepository.findBySessionId(sessionId) } returns null

        assertNull(manager.fetchReauthenticationOrNull(session))
    }
}
