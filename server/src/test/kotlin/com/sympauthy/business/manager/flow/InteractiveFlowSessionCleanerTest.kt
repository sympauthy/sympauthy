package com.sympauthy.business.manager.flow

import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.repository.InteractiveFlowSessionConfirmRepository
import com.sympauthy.data.repository.InteractiveFlowSessionLinkProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import com.sympauthy.data.repository.InteractiveFlowSessionProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionReauthenticationRepository
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import com.sympauthy.monitoring.telemetry.FlowMetrics
import com.sympauthy.monitoring.telemetry.JobMetrics
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionCleanerTest {

    @MockK
    lateinit var sessionRepository: InteractiveFlowSessionRepository

    @MockK
    lateinit var oauth2Repository: InteractiveFlowSessionOAuth2Repository

    @MockK
    lateinit var providerRepository: InteractiveFlowSessionProviderRepository

    @MockK
    lateinit var confirmRepository: InteractiveFlowSessionConfirmRepository

    @MockK
    lateinit var reauthenticationRepository: InteractiveFlowSessionReauthenticationRepository

    @MockK
    lateinit var linkProviderRepository: InteractiveFlowSessionLinkProviderRepository

    @MockK
    lateinit var securityContextRepository: InteractiveFlowSessionSecurityContextRepository

    @MockK
    lateinit var validationCodeRepository: ValidationCodeRepository

    @SpyK
    var flowMetrics = FlowMetrics(OpenTelemetry.noop())

    @SpyK
    var jobMetrics = JobMetrics(OpenTelemetry.noop())

    @InjectMockKs
    lateinit var cleaner: InteractiveFlowSessionCleaner

    private val expiredSessionId = UUID.randomUUID()

    @Test
    fun `clean - Removes everything referencing a session before the session`() = runTest {
        expiredSessions(expiredSessionId)

        val result = cleaner.clean()

        assertEquals(1, result.sessionCount)
        // One pair per dependency rather than one block over all of them: each has to precede the session
        // delete, and none of them is ordered against another.
        dependencyDeletes().forEach { dependency ->
            coVerifyOrder {
                dependency()
                sessionRepository.deleteByIds(listOf(expiredSessionId))
            }
        }
    }

    @Test
    fun `clean - Removes nothing when nothing expired`() = runTest {
        expiredSessions()

        val result = cleaner.clean()

        assertEquals(0, result.sessionCount)
        assertEquals(0, result.validationCodesCount)
    }

    @Test
    fun `clean - Counts a session that expired unfinished under the first purpose it had not finished`() = runTest {
        expiredSessions(expiredSessionId) {
            InteractiveFlowSessionEntity(
                purposes = arrayOf("CONFIRM", "MFA_ENROLLMENT"),
                initiatingPurpose = "MFA_ENROLLMENT",
                initiatingClientId = "app",
                completedPurposes = arrayOf("CONFIRM"),
                sessionDate = LocalDateTime.now(),
                expirationDate = LocalDateTime.now().minusHours(1)
            )
        }

        cleaner.clean()

        verify { flowMetrics.recordExpired("mfa_enrollment", "mfa_enrollment", "app") }
    }

    @Test
    fun `clean - Does not count a session that ended before it expired`() = runTest {
        expiredSessions(expiredSessionId) {
            InteractiveFlowSessionEntity(
                purposes = arrayOf("OAUTH2_AUTHORIZE"),
                initiatingPurpose = "OAUTH2_AUTHORIZE",
                sessionDate = LocalDateTime.now(),
                expirationDate = LocalDateTime.now().minusHours(1),
                cancelDate = LocalDateTime.now().minusHours(2)
            )
        }

        cleaner.clean()

        verify(exactly = 0) { flowMetrics.recordExpired(any(), any(), any()) }
    }

    private fun dependencyDeletes(): List<suspend () -> Int> = listOf(
        { validationCodeRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { oauth2Repository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { providerRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { confirmRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { reauthenticationRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { linkProviderRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { securityContextRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
    )

    private fun expiredSessions(
        vararg ids: UUID,
        session: () -> InteractiveFlowSessionEntity = {
            InteractiveFlowSessionEntity(
                purposes = arrayOf("OAUTH2_AUTHORIZE"),
                initiatingPurpose = "OAUTH2_AUTHORIZE",
                sessionDate = LocalDateTime.now(),
                expirationDate = LocalDateTime.now().minusHours(1)
            )
        }
    ) {
        val sessions = ids.map { id -> session().apply { this.id = id } }
        val idList = ids.toList()
        coEvery { sessionRepository.findExpired() } returns sessions
        coEvery { validationCodeRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { oauth2Repository.deleteBySessionIdIn(idList) } returns 0
        coEvery { providerRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { confirmRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { reauthenticationRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { linkProviderRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { securityContextRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { sessionRepository.deleteByIds(idList) } returns ids.size
    }
}
