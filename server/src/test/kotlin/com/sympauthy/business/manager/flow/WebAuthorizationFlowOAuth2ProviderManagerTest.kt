package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.flow.reauth.WebAuthorizationFlowProviderAttachManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.flow.reauth.ReAuthenticationCompletionDispatcher
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderClaimsResolver
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.reauth.ReAuthenticationManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.business.model.reauth.PassedReAuthenticationAttempt
import com.sympauthy.business.model.reauth.PendingReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationMethod
import com.sympauthy.business.model.reauth.ReAuthenticationPurpose
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.client.oauth2.TokenEndpointClient
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.UrlsConfig
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowOAuth2ProviderManagerTest {

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var providerAttachManager: WebAuthorizationFlowProviderAttachManager

    @MockK
    lateinit var providerConfigManager: ProviderManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @MockK
    lateinit var providerClaimsResolver: ProviderClaimsResolver

    @MockK
    lateinit var reAuthenticationManager: ReAuthenticationManager

    @MockK
    lateinit var reAuthenticationCompletionDispatcher: ReAuthenticationCompletionDispatcher

    @MockK
    lateinit var webAuthorizationFlowManager: WebAuthorizationFlowManager

    @MockK
    lateinit var tokenEndpointClient: TokenEndpointClient

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    @MockK
    lateinit var uncheckedUrlsConfig: UrlsConfig

    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowOAuth2ProviderManager

    private fun createProvider(): EnabledProvider {
        return EnabledProvider(
            id = "test-provider",
            name = "Test Provider",
            userInfo = mockk<ProviderUserInfoConfig>(),
            auth = mockk<ProviderOAuth2Config>()
        )
    }

    private fun createUser(): User {
        return User(
            id = UUID.randomUUID(),
            status = UserStatus.ENABLED,
            creationDate = LocalDateTime.now()
        )
    }

    private fun attempt(clientId: String = "client-id") = OnGoingAuthorizeAttempt(
        id = UUID.randomUUID(),
        authorizationFlowId = "flow",
        expirationDate = LocalDateTime.now().plusMinutes(30),
        attemptDate = LocalDateTime.now(),
        clientId = clientId,
        redirectUri = "https://client/callback",
        requestedScopes = emptyList(),
        userId = null,
        consentedScopes = null,
        grantedScopes = null
    )

    // --- getOAuth2 ---

    @Test
    fun `getOAuth2 - Return config when provider uses OAuth2`() {
        val oauth2Config = mockk<ProviderOAuth2Config>()
        val provider = EnabledProvider(
            id = "test-provider",
            name = "Test Provider",
            userInfo = mockk<ProviderUserInfoConfig>(),
            auth = oauth2Config
        )

        val result = manager.getOAuth2(provider)

        assertSame(oauth2Config, result)
    }

    // --- signUpOrStartAttach ---

    @Test
    fun `signUpOrStartAttach - Creates and authenticates a new account when no existing user`() = runTest {
        val att = attempt()
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(subject = "sub-123", email = "new@example.com")
        val newUser = createUser()
        val emailClaim = mockk<Claim>()
        val authenticated = att.copy(userId = newUser.id)

        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        coEvery { userManager.findByIdentifierClaims(mapOf("email" to "new@example.com")) } returns null
        coJustRun { webAuthorizationFlowManager.checkSignUpAllowed(att, false) }
        coEvery { userManager.createUser() } returns newUser
        coJustRun { collectedClaimManager.update(newUser, any()) }
        coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, providerUserInfo) }
        coJustRun { invitationManager.applyInvitationClaimsAndConsume(any(), newUser.id) }
        coEvery { authorizeAttemptManager.setAuthenticatedUserId(att, newUser.id) } returns authenticated

        val result = manager.signUpOrStartAttach(att, provider, providerUserInfo)

        assertSame(authenticated, result)
        coVerify(exactly = 0) { providerAttachManager.startAttach(any(), any(), any(), any()) }
    }

    @Test
    fun `signUpOrStartAttach - Starts attach when identifier claims collide and attach enabled`() = runTest {
        val att = attempt()
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(subject = "sub-123", email = "existing@example.com")
        val existingUser = createUser()
        val emailClaim = mockk<Claim>()
        val client = mockk<Client>()
        val audience = mockk<Audience>()
        val started = mockk<OnGoingAuthorizeAttempt>()

        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        coEvery { userManager.findByIdentifierClaims(mapOf("email" to "existing@example.com")) } returns existingUser
        coEvery { clientManager.findClientById(att.clientId) } returns client
        every { client.audience } returns audience
        every { audience.providerAttachEnabled } returns true
        coEvery { providerClaimsManager.findByUserIdAndProviderIdOrNull(existingUser.id, provider.id) } returns null
        coEvery { providerAttachManager.startAttach(att, existingUser, provider, providerUserInfo) } returns started

        val result = manager.signUpOrStartAttach(att, provider, providerUserInfo)

        assertSame(started, result)
        coVerify(exactly = 0) { authorizeAttemptManager.setAuthenticatedUserId(any(), any()) }
    }

    @Test
    fun `signUpOrStartAttach - Rejects when identifier claims collide and attach disabled`() = runTest {
        val att = attempt()
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(subject = "sub-123", email = "existing@example.com")
        val existingUser = createUser()
        val emailClaim = mockk<Claim>()
        val client = mockk<Client>()
        val audience = mockk<Audience>()

        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        coEvery { userManager.findByIdentifierClaims(mapOf("email" to "existing@example.com")) } returns existingUser
        coEvery { clientManager.findClientById(att.clientId) } returns client
        every { client.audience } returns audience
        every { audience.providerAttachEnabled } returns false

        val exception = assertThrows<BusinessException> {
            manager.signUpOrStartAttach(att, provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.existing_user", exception.detailsId)
        coVerify(exactly = 0) { providerAttachManager.startAttach(any(), any(), any(), any()) }
    }

    @Test
    fun `signUpOrStartAttach - Rejects when target account already has this provider linked`() = runTest {
        val att = attempt()
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(subject = "sub-123", email = "existing@example.com")
        val existingUser = createUser()
        val emailClaim = mockk<Claim>()
        val client = mockk<Client>()
        val audience = mockk<Audience>()

        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        coEvery { userManager.findByIdentifierClaims(mapOf("email" to "existing@example.com")) } returns existingUser
        coEvery { clientManager.findClientById(att.clientId) } returns client
        every { client.audience } returns audience
        every { audience.providerAttachEnabled } returns true
        coEvery { providerClaimsManager.findByUserIdAndProviderIdOrNull(existingUser.id, provider.id) } returns mockk()

        val exception = assertThrows<BusinessException> {
            manager.signUpOrStartAttach(att, provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.existing_user", exception.detailsId)
        coVerify(exactly = 0) { providerAttachManager.startAttach(any(), any(), any(), any()) }
    }

    @Test
    fun `signUpOrStartAttach - Throws when identifier claim not provided by provider`() = runTest {
        val att = attempt()
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(subject = "sub-123", email = null)

        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)

        val exception = assertThrows<BusinessException> {
            manager.signUpOrStartAttach(att, provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.missing_identifier_claim", exception.detailsId)
        assertEquals("email", exception.values["claim"])
    }

    @Test
    fun `signUpOrStartAttach - Throws when identifier claim not configured`() = runTest {
        val att = attempt()
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(subject = "sub-123", email = "user@example.com")

        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns null

        val exception = assertThrows<BusinessException> {
            manager.signUpOrStartAttach(att, provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.missing_identifier_claim_config", exception.detailsId)
        assertEquals("email", exception.values["claim"])
    }

    // --- completeProviderReAuthentication ---

    private fun pending(reAuthId: UUID, targetUserId: UUID) = PendingReAuthenticationAttempt(
        id = reAuthId,
        targetUserId = targetUserId,
        purpose = ReAuthenticationPurpose.PROVIDER_ATTACH,
        expirationDate = LocalDateTime.now().plusMinutes(30),
        attemptDate = LocalDateTime.now()
    )

    @Test
    fun `completeProviderReAuthentication - Completes attach when re-auth provider maps to the target account`() =
        runTest {
            val reAuthId = UUID.randomUUID()
            val targetUserId = UUID.randomUUID()
            val att = attempt().copy(reauthenticationAttemptId = reAuthId)
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(subject = "sub-linked", email = "user@example.com")
            val reAuth = pending(reAuthId, targetUserId)
            val existingUserInfo = mockk<ProviderUserInfo> { every { userId } returns targetUserId }
            val passed = mockk<PassedReAuthenticationAttempt>()
            val completed = mockk<AuthorizeAttempt>()

            coEvery { reAuthenticationManager.getPendingOrNull(reAuthId) } returns reAuth
            coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-linked") } returns existingUserInfo
            coJustRun { providerClaimsManager.refreshUserInfo(existingUserInfo, providerUserInfo) }
            coEvery { reAuthenticationManager.markPassed(reAuth, ReAuthenticationMethod.PROVIDER) } returns passed
            coEvery { reAuthenticationCompletionDispatcher.complete(att, passed) } returns completed

            val result = manager.completeProviderReAuthentication(att, provider, providerUserInfo)

            assertSame(completed, result)
        }

    @Test
    fun `completeProviderReAuthentication - Stays pending when re-auth provider maps to a different account`() =
        runTest {
            val reAuthId = UUID.randomUUID()
            val targetUserId = UUID.randomUUID()
            val att = attempt().copy(reauthenticationAttemptId = reAuthId)
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(subject = "sub-other", email = "user@example.com")
            val reAuth = pending(reAuthId, targetUserId)
            val existingUserInfo = mockk<ProviderUserInfo> { every { userId } returns UUID.randomUUID() }

            coEvery { reAuthenticationManager.getPendingOrNull(reAuthId) } returns reAuth
            coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-other") } returns existingUserInfo

            val result = manager.completeProviderReAuthentication(att, provider, providerUserInfo)

            assertSame(att, result)
            coVerify(exactly = 0) { reAuthenticationCompletionDispatcher.complete(any(), any()) }
            coVerify(exactly = 0) { reAuthenticationManager.markPassed(any(), any()) }
        }

    @Test
    fun `completeProviderReAuthentication - Stays pending when the re-authentication has expired`() = runTest {
        val reAuthId = UUID.randomUUID()
        val att = attempt().copy(reauthenticationAttemptId = reAuthId)
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(subject = "sub-linked", email = "user@example.com")

        coEvery { reAuthenticationManager.getPendingOrNull(reAuthId) } returns null

        val result = manager.completeProviderReAuthentication(att, provider, providerUserInfo)

        assertSame(att, result)
        coVerify(exactly = 0) { reAuthenticationCompletionDispatcher.complete(any(), any()) }
    }
}
