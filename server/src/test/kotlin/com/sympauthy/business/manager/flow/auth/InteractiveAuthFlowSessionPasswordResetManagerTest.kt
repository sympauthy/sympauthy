package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.claim.CollectedClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.oauth2.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.flow.reset.InteractiveFlowSessionPasswordResetManager
import com.sympauthy.business.manager.password.PasswordManager
import com.sympauthy.business.manager.password.PasswordResetMessageSender
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.securitycontext.UserSecurityContextManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.validationcode.ValidationCodeGenerator
import com.sympauthy.business.manager.validationcode.ValidationCodeManager
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionPasswordReset
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.ResetPasswordPages
import com.sympauthy.business.model.password.HashedPassword
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.securitycontext.edge.observedRequestOf
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.validationcode.ValidationCode
import com.sympauthy.business.model.validationcode.ValidationCodeMedia
import com.sympauthy.business.model.validationcode.ValidationCodeReason
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.ValidationCodeConfig
import com.sympauthy.platform.monitoring.telemetry.manager.AuthenticationMetrics
import com.sympauthy.platform.monitoring.telemetry.manager.AuthenticationMetrics.Companion.AUTHENTICATION_ATTEMPTS
import com.sympauthy.platform.monitoring.telemetry.manager.InMemoryMetrics
import com.sympauthy.platform.monitoring.telemetry.manager.attributes
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveAuthFlowSessionPasswordResetManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var passwordResetManager: InteractiveFlowSessionPasswordResetManager

    @MockK
    lateinit var passwordFlowManager: InteractiveAuthFlowSessionPasswordManager

    @MockK
    lateinit var passwordManager: PasswordManager

    @MockK(relaxUnitFun = true)
    lateinit var messageSender: PasswordResetMessageSender

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @MockK
    lateinit var providerManager: ProviderManager

    @MockK
    lateinit var validationCodeGenerator: ValidationCodeGenerator

    @MockK
    lateinit var validationCodeManager: ValidationCodeManager

    @MockK(relaxed = true)
    lateinit var userSecurityContextManager: UserSecurityContextManager

    @MockK
    lateinit var uncheckedAdvancedConfig: EnabledAdvancedConfig

    private val metrics = InMemoryMetrics()

    @SpyK
    var authenticationMetrics = AuthenticationMetrics(metrics.openTelemetry)

    @InjectMockKs
    lateinit var manager: InteractiveAuthFlowSessionPasswordResetManager

    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val login = "person@example.com"
    private val address = "proven@example.com"
    private val user = User(
        id = userId, status = UserStatus.ENABLED, creationDate = LocalDateTime.now(), sessionId = null
    )

    private val flow = InteractiveFlow(
        id = "web",
        signInUri = URI.create("https://ui.example.com/sign-in"),
        collectClaimsUri = URI.create("https://ui.example.com/claims"),
        validateClaimsUri = URI.create("https://ui.example.com/claims/validate"),
        errorUri = URI.create("https://ui.example.com/error"),
        resetPassword = ResetPasswordPages(
            resetPasswordUri = URI.create("https://ui.example.com/reset"),
            validateUri = URI.create("https://ui.example.com/reset/validate"),
            chooseUri = URI.create("https://ui.example.com/reset/choose")
        )
    )

    private val session = mockk<OnGoingInteractiveFlowSession> {
        every { id } returns sessionId
        every { userId } returns null
        every { purposes } returns listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        every { initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        every { expirationDate } returns LocalDateTime.now().plusMinutes(30)
    }

    private val gated = mockk<OnGoingInteractiveFlowSession> {
        every { id } returns sessionId
        every { userId } returns null
    }

    @BeforeEach
    fun offerTheReset() {
        every { messageSender.enabled } returns true
        every { passwordFlowManager.signInEnabled } returns true
        every { uncheckedAdvancedConfig.validationCode } returns ValidationCodeConfig(
            length = 6,
            resendDelay = Duration.ofMinutes(1),
            expiration = Duration.ofMinutes(15)
        )
    }

    private fun reset(
        userId: UUID? = this.userId,
        requestedDate: LocalDateTime = LocalDateTime.now(),
        codeProvenDate: LocalDateTime? = null
    ) = InteractiveFlowSessionPasswordReset(
        sessionId = sessionId,
        login = login,
        userId = userId,
        requestedDate = requestedDate,
        codeProvenDate = codeProvenDate
    )

    private fun code(value: String, userId: UUID = this.userId, expirationDate: LocalDateTime? = null) =
        ValidationCode(
            id = UUID.randomUUID(),
            code = value,
            userId = userId,
            media = ValidationCodeMedia.EMAIL,
            reasons = listOf(ValidationCodeReason.RESET_PASSWORD),
            sessionId = sessionId,
            creationDate = LocalDateTime.now(),
            resendDate = null,
            expirationDate = expirationDate ?: LocalDateTime.now().plusMinutes(10)
        )

    /** [login] resolves to [found], under the claims a value spelled like it could be. */
    private fun loginResolvesTo(found: User?, folded: Map<String, String> = mapOf("email" to login)) {
        every { collectedClaimManager.getIdentifierFoldedValuesOf(login) } returns folded
        coEvery { userManager.findByAnyIdentifierClaimValue(folded) } returns found
    }

    private fun noProviderIsEnabled() {
        coEvery { providerManager.listEnabledProviders() } returns emptyList()
    }

    private fun expectGate() {
        coEvery {
            sessionManager.insertPurposeBefore(
                session, InteractiveFlowPurpose.PASSWORD_RESET, InteractiveFlowPurpose.OAUTH2_AUTHORIZE
            )
        } returns gated
    }

    private fun expectGateAndRecord(sentTo: UUID?) {
        expectGate()
        coEvery { passwordResetManager.fetchPasswordResetOrNull(gated) } returns null
        coEvery { passwordResetManager.savePasswordReset(gated, login, sentTo, any()) } returns reset(sentTo)
    }

    @Test
    fun `isOffered - Offered where the flow names its pages, mail is sent and passwords sign in`() {
        assertTrue(manager.isOffered(flow))
    }

    @Test
    fun `isOffered - Not offered where the flow names no reset pages`() {
        val withoutReset = InteractiveFlow(
            id = "web",
            signInUri = flow.signInUri,
            collectClaimsUri = flow.collectClaimsUri,
            validateClaimsUri = flow.validateClaimsUri,
            errorUri = flow.errorUri
        )

        assertFalse(manager.isOffered(withoutReset))
    }

    @Test
    fun `isOffered - Not offered where this server sends no mail`() {
        every { messageSender.enabled } returns false

        assertFalse(manager.isOffered(flow))
    }

    @Test
    fun `requestCode - Sends a code to the proven address of an account holding a password`() = runTest {
        noProviderIsEnabled()
        loginResolvesTo(user)
        coEvery { collectedClaimManager.findVerifiedEmailOrNull(userId) } returns address
        coEvery { passwordManager.hasPassword(user, includesExpired = true) } returns true
        val sent = code("482913")
        coEvery {
            validationCodeGenerator.generateValidationCode(
                user, gated, ValidationCodeMedia.EMAIL, listOf(ValidationCodeReason.RESET_PASSWORD)
            )
        } returns sent
        expectGateAndRecord(sentTo = userId)

        assertSame(gated, manager.requestCode(session, flow, login))

        coVerifyOrder {
            validationCodeGenerator.generateValidationCode(
                user, gated, ValidationCodeMedia.EMAIL, listOf(ValidationCodeReason.RESET_PASSWORD)
            )
            passwordResetManager.savePasswordReset(gated, login, userId, any())
            messageSender.sendCode(address, sent)
        }
    }

    @Test
    fun `requestCode - Sends nothing where the reset could not be recorded`() = runTest {
        noProviderIsEnabled()
        loginResolvesTo(user)
        coEvery { collectedClaimManager.findVerifiedEmailOrNull(userId) } returns address
        coEvery { passwordManager.hasPassword(user, includesExpired = true) } returns true
        coEvery { validationCodeGenerator.generateValidationCode(any(), any(), any(), any()) } returns code("482913")
        coEvery {
            sessionManager.insertPurposeBefore(
                session, InteractiveFlowPurpose.PASSWORD_RESET, InteractiveFlowPurpose.OAUTH2_AUTHORIZE
            )
        } returns gated
        coEvery { passwordResetManager.fetchPasswordResetOrNull(gated) } returns null
        coEvery { passwordResetManager.savePasswordReset(gated, login, userId, any()) } throws IllegalStateException()

        assertThrows<IllegalStateException> { manager.requestCode(session, flow, login) }

        coVerify(exactly = 0) { messageSender.sendCode(any(), any()) }
    }

    @Test
    fun `requestCode - Tells an email address no account uses, as an account would hold it`() = runTest {
        noProviderIsEnabled()
        loginResolvesTo(null)
        every { collectedClaimManager.getIdentifierCleanedValueOrNull("email", login) } returns "cleaned@example.com"
        expectGateAndRecord(sentTo = null)

        manager.requestCode(session, flow, login)

        coVerify { messageSender.sendNoAccount("cleaned@example.com") }
        coVerify { passwordResetManager.savePasswordReset(gated, login, null, any()) }
    }

    @Test
    fun `requestCode - Sends nothing for a username no account holds`() = runTest {
        noProviderIsEnabled()
        loginResolvesTo(null, folded = mapOf("preferred_username" to login))
        every { collectedClaimManager.getIdentifierCleanedValueOrNull("email", login) } returns null
        expectGateAndRecord(sentTo = null)

        manager.requestCode(session, flow, login)

        coVerify(exactly = 0) { messageSender.sendNoAccount(any()) }
        coVerify { passwordResetManager.savePasswordReset(gated, login, null, any()) }
    }

    @Test
    fun `requestCode - Tells an account with no password which providers it signs in with`() = runTest {
        loginResolvesTo(user)
        coEvery { collectedClaimManager.findVerifiedEmailOrNull(userId) } returns address
        coEvery { passwordManager.hasPassword(user, includesExpired = true) } returns false
        coEvery { providerClaimsManager.findByUserId(userId) } returns listOf(
            mockk<ProviderUserInfo> { every { providerId } returns "google" }
        )
        coEvery { providerManager.listEnabledProviders() } returns listOf(
            mockk<EnabledProvider> { every { id } returns "google"; every { name } returns "Google" },
            mockk<EnabledProvider> { every { id } returns "discord"; every { name } returns "Discord" }
        )
        expectGateAndRecord(sentTo = null)

        manager.requestCode(session, flow, login)

        coVerify { messageSender.sendNoPassword(address, listOf("Google")) }
        coVerify(exactly = 0) { validationCodeGenerator.generateValidationCode(any(), any(), any(), any()) }
    }

    @Test
    fun `requestCode - Sends nothing to an account whose address is not proven`() = runTest {
        noProviderIsEnabled()
        loginResolvesTo(user)
        coEvery { collectedClaimManager.findVerifiedEmailOrNull(userId) } returns null
        expectGateAndRecord(sentTo = null)

        manager.requestCode(session, flow, login)

        coVerify(exactly = 0) { messageSender.sendCode(any(), any()) }
        coVerify(exactly = 0) { messageSender.sendNoPassword(any(), any()) }
    }

    @Test
    fun `requestCode - Sends nothing to a disabled account`() = runTest {
        noProviderIsEnabled()
        loginResolvesTo(user.copy(status = UserStatus.DISABLED))
        expectGateAndRecord(sentTo = null)

        manager.requestCode(session, flow, login)

        coVerify(exactly = 0) { messageSender.sendCode(any(), any()) }
        coVerify(exactly = 0) { collectedClaimManager.findVerifiedEmailOrNull(any()) }
    }

    @Test
    fun `requestCode - Puts the gate in front of the authorization only once`() = runTest {
        noProviderIsEnabled()
        every { session.purposes } returns
            listOf(InteractiveFlowPurpose.PASSWORD_RESET, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        loginResolvesTo(null)
        every { collectedClaimManager.getIdentifierCleanedValueOrNull("email", login) } returns null
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns null
        coEvery { passwordResetManager.savePasswordReset(session, login, null, any()) } returns reset(null)

        assertSame(session, manager.requestCode(session, flow, login))

        coVerify(exactly = 0) { sessionManager.insertPurposeBefore(any(), any(), any()) }
    }

    @Test
    fun `requestCode - Sends nothing, and keeps the reset, where the same identifier is asked again too soon`() =
        runTest {
            noProviderIsEnabled()
            expectGate()
            coEvery { passwordResetManager.fetchPasswordResetOrNull(gated) } returns reset()

            assertSame(gated, manager.requestCode(session, flow, login))

            coVerify(exactly = 0) { passwordResetManager.savePasswordReset(any(), any(), any(), any()) }
            coVerify(exactly = 0) { validationCodeGenerator.generateValidationCode(any(), any(), any(), any()) }
            coVerify(exactly = 0) { messageSender.sendCode(any(), any()) }
        }

    @Test
    fun `requestCode - Sends nothing to another identifier asked too soon, and records it for the resend`() =
        runTest {
            noProviderIsEnabled()
            val requestedDate = LocalDateTime.now().minusSeconds(10)
            expectGate()
            coEvery { passwordResetManager.fetchPasswordResetOrNull(gated) } returns
                reset(requestedDate = requestedDate).copy(login = "earlier@example.com")
            coEvery { passwordResetManager.savePasswordReset(gated, login, null, requestedDate) } returns reset(null)

            manager.requestCode(session, flow, login)

            coVerify(exactly = 1) { passwordResetManager.savePasswordReset(gated, login, null, requestedDate) }
            coVerify(exactly = 0) { messageSender.sendNoAccount(any()) }
            coVerify(exactly = 0) { collectedClaimManager.getIdentifierFoldedValuesOf(any()) }
        }

    @Test
    fun `requestCode - Refuses a blank login`() = runTest {
        noProviderIsEnabled()
        val exception = assertThrows<BusinessException> { manager.requestCode(session, flow, " ") }

        assertEquals("flow.password.reset.missing_login", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `requestCode - Fails a flow that offers no reset`() = runTest {
        every { messageSender.enabled } returns false

        val exception = assertThrows<BusinessException> { manager.requestCode(session, flow, login) }

        assertEquals("flow.password.reset.disabled", exception.detailsId)
        assertFalse(exception.recoverable)
    }

    @Test
    fun `requestCode - Leaves a session whose user is established unchanged`() = runTest {
        noProviderIsEnabled()
        every { session.userId } returns userId

        assertSame(session, manager.requestCode(session, flow, login))

        coVerify(exactly = 0) { passwordResetManager.savePasswordReset(any(), any(), any(), any()) }
    }

    @Test
    fun `validateCode - Signs in the account the code proves, at the moment it proved it`() = runTest {
        val authenticated = mockk<OnGoingInteractiveFlowSession>()
        val passwordReset = reset()
        val sent = code("482913")
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns passwordReset
        coEvery { validationCodeManager.findCodeForReasonsDuringSession(session, any(), true) } returns listOf(sent)
        coEvery { userManager.findByIdOrNull(userId) } returns user
        coEvery { sessionManager.setAuthenticatedUserId(session, userId) } returns authenticated
        coJustRun { oauth2Manager.setAuthenticationDate(authenticated, passwordId = null) }
        coEvery { passwordResetManager.markCodeProven(authenticated, passwordReset) } returns passwordReset
        coJustRun { validationCodeManager.revokeValidationCodes(listOf(sent)) }

        assertSame(authenticated, manager.validateCode(session, flow, "482913", observedRequestOf()))

        coVerify { userSecurityContextManager.markProven(sessionId, any()) }
        coVerify { oauth2Manager.setAuthenticationDate(authenticated, passwordId = null) }
        coVerify { validationCodeManager.revokeValidationCodes(listOf(sent)) }
        assertEquals(
            1,
            metrics.value(
                AUTHENTICATION_ATTEMPTS,
                attributes("method" to "password", "kind" to "reset", "outcome" to "success")
            )
        )
    }

    @Test
    fun `validateCode - Refuses a wrong code, and counts the refusal`() = runTest {
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset()
        coEvery { validationCodeManager.findCodeForReasonsDuringSession(session, any(), true) } returns
            listOf(code("482913"))

        val exception = assertThrows<BusinessException> {
            manager.validateCode(session, flow, "000000", observedRequestOf())
        }

        assertEquals("flow.password.reset.invalid_code", exception.detailsId)
        assertTrue(exception.recoverable)
        assertEquals(
            1,
            metrics.value(
                AUTHENTICATION_ATTEMPTS,
                attributes(
                    "method" to "password", "kind" to "reset", "outcome" to "refused",
                    "error.type" to "flow.password.reset.invalid_code"
                )
            )
        )
    }

    @Test
    fun `validateCode - Refuses an expired code as a wrong one`() = runTest {
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset()
        coEvery { validationCodeManager.findCodeForReasonsDuringSession(session, any(), true) } returns
            listOf(code("482913", expirationDate = LocalDateTime.now().minusMinutes(1)))

        val exception = assertThrows<BusinessException> {
            manager.validateCode(session, flow, "482913", observedRequestOf())
        }

        assertEquals("flow.password.reset.invalid_code", exception.detailsId)
    }

    @Test
    fun `validateCode - Refuses any code typed for an identifier no code went to, as a wrong one`() = runTest {
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset(userId = null)

        val exception = assertThrows<BusinessException> {
            manager.validateCode(session, flow, "482913", observedRequestOf())
        }

        assertEquals("flow.password.reset.invalid_code", exception.detailsId)
        coVerify(exactly = 0) { validationCodeManager.findCodeForReasonsDuringSession(any(), any(), any()) }
    }

    @Test
    fun `validateCode - Refuses a code sent to another account asked for earlier`() = runTest {
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset()
        coEvery { validationCodeManager.findCodeForReasonsDuringSession(session, any(), true) } returns
            listOf(code("482913", userId = UUID.randomUUID()))

        val exception = assertThrows<BusinessException> {
            manager.validateCode(session, flow, "482913", observedRequestOf())
        }

        assertEquals("flow.password.reset.invalid_code", exception.detailsId)
    }

    @Test
    fun `resendCode - Asks for nothing before the resend delay has passed`() = runTest {
        noProviderIsEnabled()
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset()

        val result = manager.resendCode(session, flow)

        assertFalse(result.resent)
        coVerify(exactly = 0) { passwordResetManager.savePasswordReset(any(), any(), any(), any()) }
    }

    @Test
    fun `resendCode - Asks for nothing once the session signed in otherwise`() = runTest {
        noProviderIsEnabled()
        every { session.userId } returns userId
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns
            reset(requestedDate = LocalDateTime.now().minusMinutes(2))

        val result = manager.resendCode(session, flow)

        assertFalse(result.resent)
        coVerify(exactly = 0) { passwordResetManager.savePasswordReset(any(), any(), any(), any()) }
    }

    @Test
    fun `resendCode - Asks again once the resend delay has passed, whoever holds the login`() = runTest {
        noProviderIsEnabled()
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns
            reset(userId = null, requestedDate = LocalDateTime.now().minusMinutes(2))
        loginResolvesTo(null)
        every { collectedClaimManager.getIdentifierCleanedValueOrNull("email", login) } returns login
        coEvery { passwordResetManager.savePasswordReset(session, login, null, any()) } returns reset(userId = null)

        val result = manager.resendCode(session, flow)

        assertTrue(result.resent)
        coVerifyOrder {
            passwordResetManager.savePasswordReset(session, login, null, any())
            messageSender.sendNoAccount(login)
        }
    }

    @Test
    fun `choosePassword - Holds the new password hashed until the session completes`() = runTest {
        val passwordReset = reset(codeProvenDate = LocalDateTime.now())
        val hashed = HashedPassword(byteArrayOf(1), byteArrayOf(2))
        every { session.userId } returns userId
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns passwordReset
        coEvery { passwordManager.hashPassword("new-s3cret") } returns hashed
        coEvery { passwordResetManager.markPasswordChosen(session, passwordReset, hashed) } returns passwordReset

        assertSame(session, manager.choosePassword(session, flow, "new-s3cret"))

        coVerify { passwordResetManager.markPasswordChosen(session, passwordReset, hashed) }
        coVerify(exactly = 0) { passwordManager.replacePasswords(any(), any()) }
    }

    @Test
    fun `choosePassword - Leaves a session whose code proved nothing yet unchanged`() = runTest {
        coEvery { passwordResetManager.fetchPasswordResetOrNull(session) } returns reset()

        assertSame(session, manager.choosePassword(session, flow, "new-s3cret"))

        coVerify(exactly = 0) { passwordManager.hashPassword(any()) }
    }
}
