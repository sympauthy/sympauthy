package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderClaimsResolver
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.business.model.user.CollectedClaimUpdate
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
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var providerConfigManager: ProviderManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @MockK
    lateinit var providerClaimsResolver: ProviderClaimsResolver

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

    // --- createOrAssociateUserWithProviderUserInfo ---

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Merge when merging enabled and user exists with matching identifier claims`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "user@example.com"
        )
        val existingUser = createUser()
        val emailClaim = mockk<Claim>()

        every { uncheckedAuthConfig.userMergingEnabled } returns true
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        coEvery { userManager.findByIdentifierClaims(mapOf("email" to "user@example.com")) } returns existingUser
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
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        coEvery { userManager.findByIdentifierClaims(mapOf("email" to "new@example.com")) } returns null
        coEvery { userManager.createUser() } returns newUser
        coJustRun { collectedClaimManager.update(newUser, any()) }
        coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, providerUserInfo) }

        val result = manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)

        assertTrue(result.created)
        assertSame(newUser, result.user)
        coVerify {
            collectedClaimManager.update(newUser, withArg { updates ->
                assertEquals(1, updates.size)
                assertEquals(emailClaim, updates[0].claim)
                assertEquals(Optional.of("new@example.com"), updates[0].value)
            })
        }
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Merge with multiple identifier claims`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "user@example.com",
            phoneNumber = "+33612345678"
        )
        val existingUser = createUser()
        val emailClaim = mockk<Claim>()
        val phoneClaim = mockk<Claim>()

        every { uncheckedAuthConfig.userMergingEnabled } returns true
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL, OpenIdConnectClaimId.PHONE_NUMBER)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.PHONE_NUMBER) } returns phoneClaim
        coEvery {
            userManager.findByIdentifierClaims(mapOf("email" to "user@example.com", "phone_number" to "+33612345678"))
        } returns existingUser
        coJustRun { providerClaimsManager.saveUserInfo(provider, existingUser.id, providerUserInfo) }

        val result = manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)

        assertFalse(result.created)
        assertSame(existingUser, result.user)
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Create with multiple identifier claims when no existing user`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "new@example.com",
            phoneNumber = "+33612345678"
        )
        val newUser = createUser()
        val emailClaim = mockk<Claim>()
        val phoneClaim = mockk<Claim>()

        every { uncheckedAuthConfig.userMergingEnabled } returns true
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL, OpenIdConnectClaimId.PHONE_NUMBER)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.PHONE_NUMBER) } returns phoneClaim
        coEvery {
            userManager.findByIdentifierClaims(mapOf("email" to "new@example.com", "phone_number" to "+33612345678"))
        } returns null
        coEvery { userManager.createUser() } returns newUser
        coJustRun { collectedClaimManager.update(newUser, any()) }
        coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, providerUserInfo) }

        val result = manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)

        assertTrue(result.created)
        coVerify {
            collectedClaimManager.update(newUser, withArg { updates ->
                assertEquals(2, updates.size)
                assertTrue(updates.any { it.claim == emailClaim && it.value == Optional.of("new@example.com") })
                assertTrue(updates.any { it.claim == phoneClaim && it.value == Optional.of("+33612345678") })
            })
        }
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Throw when merging enabled but identifier claim not provided by provider`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = null
        )

        every { uncheckedAuthConfig.userMergingEnabled } returns true
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)

        val exception = assertThrows<BusinessException> {
            manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.missing_identifier_claim", exception.detailsId)
        assertEquals("email", exception.values["claim"])
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Throw when merging enabled but identifier claim not configured`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "user@example.com"
        )

        every { uncheckedAuthConfig.userMergingEnabled } returns true
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns null

        val exception = assertThrows<BusinessException> {
            manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.missing_identifier_claim_config", exception.detailsId)
        assertEquals("email", exception.values["claim"])
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Create new user when merging disabled and no existing user`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "new@example.com"
        )
        val newUser = createUser()
        val emailClaim = mockk<Claim>()

        every { uncheckedAuthConfig.userMergingEnabled } returns false
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        coEvery { userManager.findByIdentifierClaims(mapOf("email" to "new@example.com")) } returns null
        coEvery { userManager.createUser() } returns newUser
        coJustRun { collectedClaimManager.update(newUser, any()) }
        coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, providerUserInfo) }

        val result = manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)

        assertTrue(result.created)
        assertSame(newUser, result.user)
        coVerify {
            collectedClaimManager.update(newUser, withArg { updates ->
                assertEquals(1, updates.size)
                assertEquals(emailClaim, updates[0].claim)
                assertEquals(Optional.of("new@example.com"), updates[0].value)
            })
        }
        coVerify { providerClaimsManager.saveUserInfo(provider, newUser.id, providerUserInfo) }
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Throw when merging disabled and user already exists`() = runTest {
        val provider = createProvider()
        val providerUserInfo = RawProviderClaims(
            subject = "sub-123",
            email = "existing@example.com"
        )
        val existingUser = createUser()
        val emailClaim = mockk<Claim>()

        every { uncheckedAuthConfig.userMergingEnabled } returns false
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        coEvery { userManager.findByIdentifierClaims(mapOf("email" to "existing@example.com")) } returns existingUser

        val exception = assertThrows<BusinessException> {
            manager.createOrAssociateUserWithProviderUserInfo(provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.existing_user", exception.detailsId)
        coVerify(exactly = 0) { userManager.createUser() }
    }
}