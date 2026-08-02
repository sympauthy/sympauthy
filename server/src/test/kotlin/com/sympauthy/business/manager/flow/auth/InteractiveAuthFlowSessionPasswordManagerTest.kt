package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.flow.reauth.InteractiveFlowSessionReauthenticationManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.password.PasswordManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveAuthFlowSessionPasswordManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var passwordManager: PasswordManager

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var reauthenticationManager: InteractiveFlowSessionReauthenticationManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var claimValueMapper: ClaimValueMapper

    @MockK
    lateinit var userMapper: UserMapper

    @MockK
    lateinit var uncheckedAuthConfig: AuthConfig

    @SpyK
    @InjectMockKs
    lateinit var manager: InteractiveAuthFlowSessionPasswordManager

    private val login = "user@example.com"
    private val password = "s3cret"
    private val userId = UUID.randomUUID()
    private val user = User(id = userId, status = UserStatus.ENABLED, creationDate = LocalDateTime.now())

    /**
     * Stub the shared prelude of a successful credential check: sign-in enabled, the login resolves to [user]
     * and the password matches. findByLogin/signInEnabled are final, stubbed on the spy via mockk inline.
     */
    private fun stubSuccessfulCredential() {
        every { manager.signInEnabled } returns true
        coEvery { manager.findByLogin(login) } returns user
        coEvery { passwordManager.arePasswordMatching(user, password) } returns true
    }

    @Test
    fun `signInWithPassword - Re-authentication confirms the fixed user without establishing identity`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        every { session.userId } returns userId
        val advanced = mockk<InteractiveFlowSession>()
        stubSuccessfulCredential()
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.REAUTHENTICATION
        coEvery { reauthenticationManager.markPrimaryCredentialProven(session) } returns mockk()
        coEvery { engine.completeIfNecessary(session) } returns advanced

        val result = manager.signInWithPassword(session, login, password)

        assertSame(advanced, result)
        coVerify { reauthenticationManager.markPrimaryCredentialProven(session) }
        coVerify(exactly = 0) { sessionManager.setAuthenticatedUserId(any(), any(), any()) }
    }

    @Test
    fun `signInWithPassword - Re-authentication rejects a different account and never switches identity`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns UUID.randomUUID() // a different, already-fixed user
        }
        stubSuccessfulCredential()
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.REAUTHENTICATION

        val exception = assertThrows<BusinessException> {
            manager.signInWithPassword(session, login, password)
        }

        assertEquals("flow.reauthentication.wrong_account", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { reauthenticationManager.markPrimaryCredentialProven(any()) }
        coVerify(exactly = 0) { sessionManager.setAuthenticatedUserId(any(), any(), any()) }
    }

    @Test
    fun `signInWithPassword - Normal sign-in establishes identity and skips the re-authentication branch`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns null }
        val updated = mockk<OnGoingInteractiveFlowSession>()
        val advanced = mockk<InteractiveFlowSession>()
        stubSuccessfulCredential()
        coEvery { sessionManager.setAuthenticatedUserId(session, userId, any()) } returns updated
        coEvery { engine.completeIfNecessary(updated) } returns advanced

        val result = manager.signInWithPassword(session, login, password)

        assertSame(advanced, result)
        coVerify { sessionManager.setAuthenticatedUserId(session, userId, any()) }
        coVerify(exactly = 0) { reauthenticationManager.markPrimaryCredentialProven(any()) }
        // The user is not yet known on a normal sign-in, so the engine is never walked to detect the purpose.
        coVerify(exactly = 0) { engine.currentPurposeOrNull(any()) }
    }
}
