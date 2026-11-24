package com.sympauthy.business.flow.reset.manager

import com.sympauthy.business.claim.manager.CollectedClaimManager
import com.sympauthy.business.flow.model.FailedInteractiveFlowSession
import com.sympauthy.business.flow.model.InteractiveFlowSessionPasswordReset
import com.sympauthy.business.flow.model.InteractiveFlowStep
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.flow.model.PreparedTerminalEffect
import com.sympauthy.business.flow.oauth2.manager.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.grant.manager.GrantManager
import com.sympauthy.business.password.manager.PasswordManager
import com.sympauthy.business.password.manager.PasswordManager.PasswordReplacement
import com.sympauthy.business.password.manager.PasswordResetMessageSender
import com.sympauthy.business.password.model.HashedPassword
import com.sympauthy.business.user.manager.UserManager
import com.sympauthy.business.user.model.User
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class PasswordResetInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var passwordResetManager: InteractiveFlowSessionPasswordResetManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var passwordManager: PasswordManager

    @MockK
    lateinit var grantManager: GrantManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var messageSender: PasswordResetMessageSender

    @InjectMockKs
    lateinit var handler: PasswordResetInteractiveFlowPurposeHandler

    private val session = mockk<OnGoingInteractiveFlowSession>()
    private val userId = UUID.randomUUID()
    private val newPassword = HashedPassword(byteArrayOf(1), byteArrayOf(2))
    private val requestedDate = LocalDateTime.of(2026, 11, 15, 9, 50)

    private fun reset(
        userId: UUID? = this.userId,
        codeProvenDate: LocalDateTime? = null,
        newPassword: HashedPassword? = null
    ) = InteractiveFlowSessionPasswordReset(
        sessionId = UUID.randomUUID(),
        login = "person@example.com",
        userId = userId,
        requestedDate = requestedDate,
        codeProvenDate = codeProvenDate,
        newPassword = newPassword,
        passwordChosenDate = newPassword?.let { requestedDate.plusMinutes(2) }
    )

    @Test
    fun `nextStepOrNull - Asks for the code until one proves the account`() = runTest {
        every { session.userId } returns null
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset()

        assertEquals(InteractiveFlowStep.ResetPasswordValidate, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Asks for the code for an identifier no code went to`() = runTest {
        every { session.userId } returns null
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset(userId = null)

        assertEquals(InteractiveFlowStep.ResetPasswordValidate, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Asks for the new password once the code proved the account`() = runTest {
        every { session.userId } returns userId
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns
            reset(codeProvenDate = requestedDate.plusMinutes(1))

        assertEquals(InteractiveFlowStep.ResetPasswordChoose, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Is resolved once the new password is chosen`() = runTest {
        every { session.userId } returns userId
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns
            reset(codeProvenDate = requestedDate.plusMinutes(1), newPassword = newPassword)

        assertNull(handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Is resolved where the end-user signed in before proving a code`() = runTest {
        every { session.userId } returns UUID.randomUUID()
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset()

        assertNull(handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Is resolved where the session holds no reset`() = runTest {
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns null

        assertNull(handler.nextStepOrNull(session))
    }

    @Test
    fun `prepareTerminalEffect - Replaces the password, ties the grant to it, revokes the old ones, and tells`() =
        runTest {
            val user = mockk<User> { every { id } returns this@PasswordResetInteractiveFlowPurposeHandlerTest.userId }
            val newPasswordId = UUID.randomUUID()
            val replacedIds = listOf(UUID.randomUUID(), UUID.randomUUID())
            every { session.userId } returns userId
            coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns
                reset(codeProvenDate = requestedDate.plusMinutes(1), newPassword = newPassword)
            coEvery { userManager.findById(userId) } returns user
            coEvery { collectedClaimManager.findVerifiedEmailOrNull(userId) } returns "person@example.com"
            coEvery { passwordManager.replacePasswords(user, newPassword) } returns
                PasswordReplacement(newPasswordId, replacedIds)
            coJustRun { oauth2Manager.setPasswordId(session, newPasswordId) }
            coEvery { grantManager.revokeGrantsByPasswords(userId, replacedIds, any()) } returns 2
            coJustRun { messageSender.sendPasswordChanged("person@example.com", any()) }

            val effect = assertInstanceOf(
                PreparedTerminalEffect.Apply::class.java, handler.prepareTerminalEffect(session)
            )
            coVerify(exactly = 0) { passwordManager.replacePasswords(any(), any()) }
            coVerify(exactly = 0) { messageSender.sendPasswordChanged(any(), any()) }

            effect.apply()

            // The mail is written with the change it reports, inside the completion transaction.
            coVerifyOrder {
                passwordManager.replacePasswords(user, newPassword)
                oauth2Manager.setPasswordId(session, newPasswordId)
                grantManager.revokeGrantsByPasswords(userId, replacedIds, any())
                messageSender.sendPasswordChanged("person@example.com", any())
            }
        }

    @Test
    fun `prepareTerminalEffect - Has no effect for a reset that chose no password`() = runTest {
        every { session.userId } returns userId
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns
            reset(codeProvenDate = requestedDate.plusMinutes(1))

        assertEquals(PreparedTerminalEffect.None, handler.prepareTerminalEffect(session))
    }

    @Test
    fun `prepareTerminalEffect - Has no effect where the end-user signed in otherwise`() = runTest {
        every { session.userId } returns UUID.randomUUID()
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset()

        assertEquals(PreparedTerminalEffect.None, handler.prepareTerminalEffect(session))
    }

    @Test
    fun `debugInformation - Names the account a code went to and when each step was taken`() = runTest {
        val provenDate = requestedDate.plusMinutes(1)
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns
            reset(codeProvenDate = provenDate, newPassword = newPassword)

        val information = handler.debugInformation(session)

        assertEquals(
            listOf("code_sent_to_user_id", "requested_at", "code_proven_at", "password_chosen_at"),
            information.map { it.code }
        )
        assertEquals(listOf(userId.toString(), requestedDate, provenDate, requestedDate.plusMinutes(2)),
            information.map { it.value })
        // Neither the identifier typed, which may be somebody else's address, nor the new password is there.
        assertTrue(information.none { it.value == "person@example.com" })
    }

    @Test
    fun `debugInformation - Answers for a session that has no user, no record and reached a terminal status`() =
        runTest {
            val failed = mockk<FailedInteractiveFlowSession>()
            coEvery { passwordResetManager.fetchPasswordResetOrNull(failed) } returns null

            val information = handler.debugInformation(failed)

            assertEquals(4, information.size)
            assertTrue(information.all { it.value == null })
        }
}
