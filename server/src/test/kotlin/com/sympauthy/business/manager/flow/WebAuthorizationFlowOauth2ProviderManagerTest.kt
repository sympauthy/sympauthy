package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderConfigManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.config.ProviderOauth2Config
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.OpenIdClaim
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
class WebAuthorizationFlowOauth2ProviderManagerTest {

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var providerConfigManager: ProviderConfigManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

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
    lateinit var manager: WebAuthorizationFlowOauth2ProviderManager

    private fun createProvider(): EnabledProvider {
        return EnabledProvider(
            id = "test-provider",
            name = "Test Provider",
            userInfo = mockk<ProviderUserInfoConfig>(),
            auth = mockk<ProviderOauth2Config>()
        )
    }

    private fun createUser(): User {
        return User(
            id = UUID.randomUUID(),
            status = UserStatus.ENABLED,
            creationDate = LocalDateTime.now()
        )
    }

    // --- getOauth2 ---

    @Test
    fun `getOauth2 - Return config when provider uses OAuth2`() {
        val oauth2Config = mockk<ProviderOauth2Config>()
        val provider = EnabledProvider(
            id = "test-provider",
            name = "Test Provider",
            userInfo = mockk<ProviderUserInfoConfig>(),
            auth = oauth2Config
        )

        val result = manager.getOauth2(provider)

        assertSame(oauth2Config, result)
    }

    // --- createOrAssociateUserWithProviderUserInfo ---

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Merge by email when user merging enabled and user exists`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "user@example.com"
        )
        val existingUser = createUser()
        val emailClaim = mockk<Claim>()

        every { uncheckedAuthConfig.userMergingEnabled } returns true
        every { claimManager.findById(OpenIdClaim.Id.EMAIL) } returns emailClaim
        coEvery { userManager.findByEmail("user@example.com") } returns existingUser
        coJustRun { providerClaimsManager.saveUserInfo(provider, existingUser.id, providerUserInfo) }

        val result = manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)

        assertFalse(result.created)
        assertSame(existingUser, result.user)
        coVerify { providerClaimsManager.saveUserInfo(provider, existingUser.id, providerUserInfo) }
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Create new user when merging enabled but no existing user`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "new@example.com"
        )
        val newUser = createUser()
        val emailClaim = mockk<Claim>()

        every { uncheckedAuthConfig.userMergingEnabled } returns true
        every { claimManager.findById(OpenIdClaim.Id.EMAIL) } returns emailClaim
        coEvery { userManager.findByEmail("new@example.com") } returns null
        coEvery { userManager.createUser() } returns newUser
        coJustRun { collectedClaimManager.update(newUser, any()) }
        coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, providerUserInfo) }

        val result = manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)

        assertTrue(result.created)
        assertSame(newUser, result.user)
        coVerify { collectedClaimManager.update(newUser, any()) }
        coVerify { providerClaimsManager.saveUserInfo(provider, newUser.id, providerUserInfo) }
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Throw when merging enabled but no email provided`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = null
        )

        every { uncheckedAuthConfig.userMergingEnabled } returns true

        val exception = assertThrows<BusinessException> {
            manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.missing_identifier_claim", exception.detailsId)
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Throw when merging enabled but email claim not configured`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "user@example.com"
        )

        every { uncheckedAuthConfig.userMergingEnabled } returns true
        every { claimManager.findById(OpenIdClaim.Id.EMAIL) } returns null

        val exception = assertThrows<BusinessException> {
            manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.missing_identifier_claim_config", exception.detailsId)
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Delegate to createUserWithProviderUserInfo when merging disabled`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "user@example.com"
        )

        every { uncheckedAuthConfig.userMergingEnabled } returns false

        // createUserWithProviderUserInfo has a TODO("FIXME"), so it should throw NotImplementedError
        assertThrows<NotImplementedError> {
            manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)
        }

        // Verify we never tried to merge by email
        coVerify(exactly = 0) { userManager.findByEmail(any()) }
    }
}