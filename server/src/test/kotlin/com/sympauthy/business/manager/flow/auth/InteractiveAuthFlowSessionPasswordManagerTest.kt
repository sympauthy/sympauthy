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
import com.sympauthy.business.manager.user.TakenIdentifier
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import com.sympauthy.business.manager.security.UserSecurityContextManager
import com.sympauthy.business.model.security.observedRequestOf
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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

    @MockK(relaxed = true)
    lateinit var userSecurityContextManager: UserSecurityContextManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var userMapper: UserMapper

    @MockK
    lateinit var uncheckedAuthConfig: AuthConfig

    @SpyK
    @InjectMockKs
    lateinit var manager: InteractiveAuthFlowSessionPasswordManager

    private val login = "user@example.com"
    private val password = "s3cret"
    private val sessionId = UUID.randomUUID()
    /** The account a refusal names as holding the value, which reaches an operator and never the person refused. */
    private val ownerId = UUID.randomUUID()

    private val userId = UUID.randomUUID()
    private val user = User(
        id = userId,
        status = UserStatus.ENABLED,
        creationDate = LocalDateTime.now(),
        sessionId = null
    )

    /**
     * Stub the shared prelude of a successful credential check: sign-in enabled, the login resolves to [user]
     * and the password matches. The two reads are final, stubbed on the spy via mockk inline.
     */
    private fun stubSuccessfulCredential() {
        every { manager.signInEnabled } returns true
        coEvery { manager.findByAnyIdentifierClaimValue(login) } returns user
        coEvery { passwordManager.arePasswordMatching(user, password) } returns true
    }

    @Test
    fun `findByAnyIdentifierClaimValue - Hands the value folded per claim to the account read`() = runTest {
        // Which fold each claim gets is CollectedClaimManagerTest's, and which row answers is
        // UserManagerTest's; what this holds is that the one is what reaches the other.
        val folded = mapOf("email" to login)
        every { collectedClaimManager.getIdentifierFoldedValuesOf(login) } returns folded
        coEvery { userManager.findByAnyIdentifierClaimValue(folded) } returns user

        assertSame(user, manager.findByAnyIdentifierClaimValue(login))
    }

    @Test
    fun `findByAnyIdentifierClaimValue - Answers none where no committed claim holds the value`() = runTest {
        every { collectedClaimManager.getIdentifierFoldedValuesOf(login) } returns mapOf("email" to login)
        coEvery { userManager.findByAnyIdentifierClaimValue(any()) } returns null

        assertNull(manager.findByAnyIdentifierClaimValue(login))
    }

    @Test
    fun `signInWithPassword - Re-authentication confirms the fixed user without establishing identity`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        every { session.userId } returns userId
        every { session.id } returns sessionId
        val advanced = mockk<InteractiveFlowSession>()
        stubSuccessfulCredential()
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.REAUTHENTICATION
        coEvery { reauthenticationManager.markPrimaryCredentialProven(session) } returns mockk()
        coEvery { engine.completeIfNecessary(session) } returns advanced

        val result = manager.signInWithPassword(session, login, password, observedRequestOf())

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
            manager.signInWithPassword(session, login, password, observedRequestOf())
        }

        assertEquals("flow.reauthentication.wrong_account", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { reauthenticationManager.markPrimaryCredentialProven(any()) }
        coVerify(exactly = 0) { sessionManager.setAuthenticatedUserId(any(), any(), any()) }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `signInWithPassword - Does not establish or switch identity when a later purpose is active after re-auth`() = runTest {
        // Regression for the confirm-never-establish gap: once REAUTHENTICATION has resolved and a later
        // purpose (e.g. MFA_CHALLENGE) is active, a re-submitted sign-in POST must NOT fall through to the
        // establish path and switch the already-fixed session user.
        val session = mockk<OnGoingInteractiveFlowSession>()
        every { session.userId } returns UUID.randomUUID() // already fixed
        stubSuccessfulCredential()
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.MFA_CHALLENGE

        val result = manager.signInWithPassword(session, login, password, observedRequestOf())

        assertSame(session, result)
        coVerify(exactly = 0) { sessionManager.setAuthenticatedUserId(any(), any(), any()) }
        coVerify(exactly = 0) { reauthenticationManager.markPrimaryCredentialProven(any()) }
    }

    @Test
    fun `signInWithPassword - Normal sign-in establishes identity and skips the re-authentication branch`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns null
            every { id } returns sessionId
        }
        val updated = mockk<OnGoingInteractiveFlowSession>()
        val advanced = mockk<InteractiveFlowSession>()
        stubSuccessfulCredential()
        coEvery { sessionManager.setAuthenticatedUserId(session, userId, any()) } returns updated
        coEvery { engine.completeIfNecessary(updated) } returns advanced

        val result = manager.signInWithPassword(session, login, password, observedRequestOf())

        assertSame(advanced, result)
        coVerify { sessionManager.setAuthenticatedUserId(session, userId, any()) }
        coVerify(exactly = 0) { reauthenticationManager.markPrimaryCredentialProven(any()) }
        // The user is not yet known on a normal sign-in, so the engine is never walked to detect the purpose.
        coVerify(exactly = 0) { engine.currentPurposeOrNull(any()) }
    }

    /**
     * The password is verified against whatever account matches the login, which is not necessarily this
     * session's. Anybody holding the session's state — which carries no identity and travels in a URL —
     * can post their own valid credentials into a flow already fixed to somebody else, and the place they
     * did it from must not be recorded as that person's.
     */
    @Test
    fun `signInWithPassword - Records nothing where the credential resolved no user for this session`() = runTest {
        // The session is left unable to answer for its id on purpose: observing reads it, so a call that
        // reached the observation would fail here rather than quietly record the wrong person's address.
        val session = mockk<OnGoingInteractiveFlowSession>()
        every { session.userId } returns UUID.randomUUID()
        stubSuccessfulCredential()
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.MFA_CHALLENGE

        manager.signInWithPassword(session, login, password, observedRequestOf())

        coVerify(exactly = 0) { userSecurityContextManager.markProven(any(), any()) }
    }

    @Test
    fun `signInWithPassword - Records nothing where the password did not verify`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        every { manager.signInEnabled } returns true
        coEvery { manager.findByAnyIdentifierClaimValue(login) } returns user
        coEvery { passwordManager.arePasswordMatching(user, password) } returns false

        assertThrows<BusinessException> {
            manager.signInWithPassword(session, login, password, observedRequestOf())
        }

        coVerify(exactly = 0) { userSecurityContextManager.markProven(any(), any()) }
    }

    @Test
    fun `signInWithPassword - Records nothing where re-authentication named a different account`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns UUID.randomUUID()
        }
        stubSuccessfulCredential()
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.REAUTHENTICATION

        assertThrows<BusinessException> {
            manager.signInWithPassword(session, login, password, observedRequestOf())
        }

        coVerify(exactly = 0) { userSecurityContextManager.markProven(any(), any()) }
    }

    @Test
    fun `checkForConflictingUsers - Refuses recoverably when a committed account holds one value`() = runTest {
        val updates = listOf(claimUpdate("email", "a@example.com"), claimUpdate("phone_number", "+33612345678"))
        val offered = mapOf("email" to "\"a@example.com\"", "phone_number" to "\"+33612345678\"")
        every { collectedClaimManager.getIdentifierFoldedValuesIn(updates) } returns offered
        coEvery {
            userManager.findTakenIdentifierOrNull(null, listOf("email", "phone_number"), offered)
        } returns TakenIdentifier(claimId = "email", userId = ownerId)

        val exception = assertThrows<BusinessException> { manager.checkForConflictingUsers(updates) }

        assertEquals("flow.password.sign_up.existing", exception.detailsId)
        assertTrue(exception.recoverable)
        assertEquals("email", exception.values["claim"])
        assertEquals(ownerId.toString(), exception.values["userId"])
    }

    @Test
    fun `checkForConflictingUsers - Names no account, since the one being signed up does not exist yet`() =
        runTest {
            val updates = listOf(claimUpdate("email", "a@example.com"))
            val offered = mapOf("email" to "\"a@example.com\"")
            every { collectedClaimManager.getIdentifierFoldedValuesIn(updates) } returns offered
            coEvery { userManager.findTakenIdentifierOrNull(null, any(), any()) } returns null

            manager.checkForConflictingUsers(updates)

            coVerify { userManager.findTakenIdentifierOrNull(null, listOf("email"), offered) }
        }

    @Test
    fun `checkForConflictingUsers - Searches a claim the offered values are silent about`() = runTest {
        val updates = listOf(claimUpdate("email", "a@example.com"), CollectedClaimUpdate(claimOf("name"), null))
        // An update clearing a claim carries no value — which claims carry one is
        // CollectedClaimManagerTest's — and the claim is searched all the same, because a value has to be
        // free across every one of them.
        val offered = mapOf("email" to "\"a@example.com\"")
        every { collectedClaimManager.getIdentifierFoldedValuesIn(updates) } returns offered
        coEvery { userManager.findTakenIdentifierOrNull(null, any(), any()) } returns null

        manager.checkForConflictingUsers(updates)

        coVerify { userManager.findTakenIdentifierOrNull(null, listOf("email", "name"), offered) }
    }

    @Test
    fun `checkForMissingClaims - Refuses recoverably a claim no update was submitted for`() {
        val email = claimOf("email")

        val exception = assertThrows<BusinessException> { manager.checkForMissingClaims(mapOf(email to null)) }

        assertEquals("flow.password.sign_up.missing_claim", exception.detailsId)
        assertTrue(exception.recoverable)
        assertEquals("email", exception.values["claim"])
    }

    @Test
    fun `checkForMissingClaims - Refuses recoverably a claim whose update carries no value`() {
        val email = claimOf("email")
        val cleared = CollectedClaimUpdate(claim = email, value = Optional.empty())

        val exception = assertThrows<BusinessException> { manager.checkForMissingClaims(mapOf(email to cleared)) }

        assertEquals("flow.password.sign_up.missing_claim", exception.detailsId)
        assertTrue(exception.recoverable)
        assertEquals("email", exception.values["claim"])
    }

    @Test
    fun `checkForMissingClaims - Names the claim missing a value rather than one carrying it`() {
        val email = mockk<Claim>()
        val username = claimOf("preferred_username")

        val exception = assertThrows<BusinessException> {
            manager.checkForMissingClaims(
                mapOf(
                    email to CollectedClaimUpdate(email, Optional.of("a@example.com")),
                    username to CollectedClaimUpdate(username, Optional.empty())
                )
            )
        }

        assertEquals("preferred_username", exception.values["claim"])
    }

    @Test
    fun `checkForMissingClaims - Accepts every claim carrying a value`() {
        val email = mockk<Claim>()
        val username = mockk<Claim>()

        assertDoesNotThrow {
            manager.checkForMissingClaims(
                mapOf(
                    email to CollectedClaimUpdate(email, Optional.of("a@example.com")),
                    username to CollectedClaimUpdate(username, Optional.of("ada.lovelace"))
                )
            )
        }
    }

    @Test
    fun `createAccountWithClaimsAndPassword - Refuses a claim with no value before creating the account`() =
        runTest {
            val session = mockk<OnGoingInteractiveFlowSession>()
            val email = claimOf("email")
            val oauth2 = mockk<InteractiveFlowSessionOAuth2>()
            coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
            coEvery { interactiveAuthFlowSessionManager.checkSignUpAllowed(oauth2, true) } returns Unit
            every { claimManager.listIdentifierClaims() } returns listOf(email)

            val exception = assertThrows<BusinessException> {
                manager.createAccountWithClaimsAndPassword(
                    session = session,
                    unfilteredUpdates = listOf(CollectedClaimUpdate(email, Optional.empty())),
                    password = password
                )
            }

            assertEquals("flow.password.sign_up.missing_claim", exception.detailsId)
            coVerify(exactly = 0) { userManager.createUser(any()) }
        }

    private fun claimUpdate(claimId: String, value: String) = CollectedClaimUpdate(
        claim = claimOf(claimId),
        value = Optional.of(value)
    )

    private fun claimOf(claimId: String) = mockk<Claim> { every { id } returns claimId }

    @Test
    fun `signInWithPassword - Records where a normal sign-in established identity`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns null
            every { id } returns sessionId
        }
        val updated = mockk<OnGoingInteractiveFlowSession>()
        stubSuccessfulCredential()
        coEvery { sessionManager.setAuthenticatedUserId(session, userId, any()) } returns updated
        coEvery { engine.completeIfNecessary(updated) } returns mockk()

        manager.signInWithPassword(session, login, password, observedRequestOf())

        coVerify { userSecurityContextManager.markProven(sessionId, any()) }
    }

    @Test
    fun `signInWithPassword - Records where re-authentication confirmed this session's user`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        every { session.userId } returns userId
        every { session.id } returns sessionId
        stubSuccessfulCredential()
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.REAUTHENTICATION
        coEvery { reauthenticationManager.markPrimaryCredentialProven(session) } returns mockk()
        coEvery { engine.completeIfNecessary(session) } returns mockk()

        manager.signInWithPassword(session, login, password, observedRequestOf())

        coVerify { userSecurityContextManager.markProven(sessionId, any()) }
    }
}
