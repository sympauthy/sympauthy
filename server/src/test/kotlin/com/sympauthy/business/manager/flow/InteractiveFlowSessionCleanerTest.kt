package com.sympauthy.business.manager.flow

import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.InteractiveFlowSessionConfirmRepository
import com.sympauthy.data.repository.InteractiveFlowSessionLinkProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import com.sympauthy.data.repository.InteractiveFlowSessionProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionReauthenticationRepository
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
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
    lateinit var validationCodeRepository: ValidationCodeRepository

    @MockK
    lateinit var authorizationCodeRepository: AuthorizationCodeRepository

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
        assertEquals(0, result.authorizationCodeCount)
        assertEquals(0, result.validationCodesCount)
    }

    private fun dependencyDeletes(): List<suspend () -> Int> = listOf(
        { authorizationCodeRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { validationCodeRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { oauth2Repository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { providerRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { confirmRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { reauthenticationRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
        { linkProviderRepository.deleteBySessionIdIn(listOf(expiredSessionId)) },
    )

    private fun expiredSessions(vararg ids: UUID) {
        val sessions = ids.map { id ->
            InteractiveFlowSessionEntity(
                purposes = arrayOf("OAUTH2_AUTHORIZE"),
                initiatingPurpose = "OAUTH2_AUTHORIZE",
                sessionDate = LocalDateTime.now(),
                expirationDate = LocalDateTime.now().minusHours(1)
            ).apply { this.id = id }
        }
        val idList = ids.toList()
        coEvery { sessionRepository.findExpired() } returns sessions
        coEvery { authorizationCodeRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { validationCodeRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { oauth2Repository.deleteBySessionIdIn(idList) } returns 0
        coEvery { providerRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { confirmRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { reauthenticationRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { linkProviderRepository.deleteBySessionIdIn(idList) } returns 0
        coEvery { sessionRepository.deleteByIds(idList) } returns ids.size
    }
}
