package com.sympauthy.business.manager.flow.reauth

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.reauth.ReAuthenticationManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.business.model.reauth.PassedReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationMethod
import com.sympauthy.business.model.reauth.ReAuthenticationPurpose
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.repository.PasswordRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowProviderAttachManagerTest {

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var passwordRepository: PasswordRepository

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @MockK
    lateinit var providerConfigManager: ProviderManager

    @MockK
    lateinit var reAuthenticationManager: ReAuthenticationManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var webAuthorizationFlowManager: WebAuthorizationFlowManager

    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowProviderAttachManager

    private fun createProvider() = EnabledProvider(
        id = "test-provider",
        name = "Test Provider",
        userInfo = mockk<ProviderUserInfoConfig>(),
        auth = mockk<ProviderOAuth2Config>()
    )

    private fun createUser() = User(
        id = UUID.randomUUID(),
        status = UserStatus.ENABLED,
        creationDate = LocalDateTime.now()
    )

    private fun attempt() = OnGoingAuthorizeAttempt(
        id = UUID.randomUUID(),
        authorizationFlowId = "flow",
        expirationDate = LocalDateTime.now().plusMinutes(30),
        attemptDate = LocalDateTime.now(),
        clientId = "client-id",
        redirectUri = "https://client/callback",
        requestedScopes = emptyList(),
        userId = null,
        consentedScopes = null,
        grantedScopes = null
    )

    private fun passed(targetUserId: UUID) = PassedReAuthenticationAttempt(
        id = UUID.randomUUID(),
        targetUserId = targetUserId,
        purpose = ReAuthenticationPurpose.PROVIDER_ATTACH,
        expirationDate = LocalDateTime.now().plusMinutes(30),
        attemptDate = LocalDateTime.now(),
        passedDate = LocalDateTime.now(),
        passedMethod = ReAuthenticationMethod.PASSWORD
    )

    @Test
    fun `completeAttach - promotes the provisional identity and authenticates the target user`() = runTest {
        val att = attempt()
        val targetUserId = UUID.randomUUID()
        val reAuth = passed(targetUserId)
        val withUser = att.copy(userId = targetUserId)
        val cleared = withUser.copy(reauthenticationAttemptId = null)

        coJustRun { providerClaimsManager.confirmProvisionalUserInfo(att.id) }
        coEvery { authorizeAttemptManager.setAuthenticatedUserId(att, targetUserId) } returns withUser
        coEvery { authorizeAttemptManager.clearReAuthentication(withUser) } returns cleared

        val result = manager.completeAttach(att, reAuth)

        assertSame(cleared, result)
    }

    @Test
    fun `decline - creates a separate account when sign-up is allowed`() = runTest {
        val att = attempt()
        val provider = createProvider()
        val rawClaims = RawProviderClaims(subject = "sub-123", email = "existing@example.com")
        val provisional = mockk<ProviderUserInfo>()
        val newUser = createUser()
        val withUser = att.copy(userId = newUser.id)
        val cleared = withUser.copy(reauthenticationAttemptId = null)

        coEvery { providerClaimsManager.findProvisionalByAttempt(att.id) } returns provisional
        coJustRun { webAuthorizationFlowManager.checkSignUpAllowed(att, true) }
        every { provisional.providerId } returns provider.id
        every { provisional.userInfo } returns rawClaims
        coEvery { providerConfigManager.findByIdAndCheckEnabled(provider.id) } returns provider
        coEvery { providerClaimsManager.deleteProvisionalByAttempt(att.id) } returns 1
        coEvery { userManager.createUser() } returns newUser
        coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, rawClaims) }
        coEvery { authorizeAttemptManager.setAuthenticatedUserId(att, newUser.id) } returns withUser
        coEvery { authorizeAttemptManager.clearReAuthentication(withUser) } returns cleared

        val result = manager.decline(att)

        assertSame(cleared, result)
    }

    @Test
    fun `decline - rejects and creates no account when sign-up is not allowed`() = runTest {
        val att = attempt()
        val provisional = mockk<ProviderUserInfo>()

        coEvery { providerClaimsManager.findProvisionalByAttempt(att.id) } returns provisional
        coEvery { webAuthorizationFlowManager.checkSignUpAllowed(att, true) } throws BusinessException(
            recoverable = true,
            detailsId = "flow.sign_up.disabled"
        )

        assertThrows<BusinessException> {
            manager.decline(att)
        }

        coVerify(exactly = 0) { userManager.createUser() }
        coVerify(exactly = 0) { providerClaimsManager.deleteProvisionalByAttempt(any()) }
    }
}
