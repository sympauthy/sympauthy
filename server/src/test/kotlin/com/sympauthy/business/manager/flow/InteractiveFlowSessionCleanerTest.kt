package com.sympauthy.business.manager.flow

import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.InteractiveFlowSessionConfirmRepository
import com.sympauthy.data.repository.InteractiveFlowSessionLinkProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import com.sympauthy.data.repository.InteractiveFlowSessionProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionReauthenticationRepository
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.TotpEnrollmentRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import io.mockk.coEvery
import io.mockk.coVerify
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

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var passwordRepository: PasswordRepository

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var providerUserInfoRepository: ProviderUserInfoRepository

    @MockK
    lateinit var totpEnrollmentRepository: TotpEnrollmentRepository

    @InjectMockKs
    lateinit var cleaner: InteractiveFlowSessionCleaner

    private val expiredSessionId = UUID.randomUUID()
    private val abandonedUserId = UUID.randomUUID()

    @Test
    fun `clean - Collects the abandoned account after the session, and its rows before it`() = runTest {
        expiredSessions(expiredSessionId)
        abandonedAccounts(abandonedUserId)
        coEvery { passwordRepository.deleteByUserIdIn(listOf(abandonedUserId)) } returns 1
        coEvery { collectedClaimRepository.deleteByUserIdIn(listOf(abandonedUserId)) } returns 2
        coEvery { providerUserInfoRepository.deleteByUserIdIn(listOf(abandonedUserId)) } returns 0
        coEvery { totpEnrollmentRepository.deleteByUserIdIn(listOf(abandonedUserId)) } returns 0
        coEvery { userRepository.deleteByIdIn(listOf(abandonedUserId)) } returns 1

        val result = cleaner.clean()

        assertEquals(1, result.abandonedAccountCount)
        coVerifyOrder {
            sessionRepository.deleteByIds(listOf(expiredSessionId))
            userRepository.findAbandoned()
            passwordRepository.deleteByUserIdIn(listOf(abandonedUserId))
            collectedClaimRepository.deleteByUserIdIn(listOf(abandonedUserId))
            providerUserInfoRepository.deleteByUserIdIn(listOf(abandonedUserId))
            totpEnrollmentRepository.deleteByUserIdIn(listOf(abandonedUserId))
            userRepository.deleteByIdIn(listOf(abandonedUserId))
        }
    }

    @Test
    fun `clean - Touches no account table when nothing was abandoned`() = runTest {
        expiredSessions(expiredSessionId)
        coEvery { userRepository.findAbandoned() } returns emptyList()

        val result = cleaner.clean()

        assertEquals(0, result.abandonedAccountCount)
        coVerify(exactly = 0) { passwordRepository.deleteByUserIdIn(any()) }
        coVerify(exactly = 0) { userRepository.deleteByIdIn(any()) }
    }

    @Test
    fun `clean - Collects an account orphaned by an earlier run even with nothing expired`() = runTest {
        expiredSessions()
        abandonedAccounts(abandonedUserId)
        coEvery { passwordRepository.deleteByUserIdIn(listOf(abandonedUserId)) } returns 0
        coEvery { collectedClaimRepository.deleteByUserIdIn(listOf(abandonedUserId)) } returns 1
        coEvery { providerUserInfoRepository.deleteByUserIdIn(listOf(abandonedUserId)) } returns 0
        coEvery { totpEnrollmentRepository.deleteByUserIdIn(listOf(abandonedUserId)) } returns 0
        coEvery { userRepository.deleteByIdIn(listOf(abandonedUserId)) } returns 1

        val result = cleaner.clean()

        assertEquals(0, result.sessionCount)
        assertEquals(1, result.abandonedAccountCount)
    }

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

    private fun abandonedAccounts(vararg ids: UUID) {
        coEvery { userRepository.findAbandoned() } returns ids.map { id ->
            UserEntity(
                status = "ENABLED",
                creationDate = LocalDateTime.now(),
                sessionId = UUID.randomUUID()
            ).apply { this.id = id }
        }
    }
}
