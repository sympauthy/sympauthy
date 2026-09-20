package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager
import com.sympauthy.business.manager.flow.reauth.InteractiveFlowSessionReauthenticationManager
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderClaimsResolver
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionLinkProvider
import com.sympauthy.business.model.flow.InteractiveFlowSessionProvider
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2Tokens
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.client.oauth2.TokenEndpointClient
import com.sympauthy.data.repository.ObjectLockRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import com.sympauthy.business.manager.security.UserSecurityContextManager
import com.sympauthy.business.model.security.observedRequestOf
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionOAuth2ProviderManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var providerManager: InteractiveFlowSessionProviderManager

    @MockK
    lateinit var reauthenticationManager: InteractiveFlowSessionReauthenticationManager

    @MockK(relaxed = true)
    lateinit var userSecurityContextManager: UserSecurityContextManager

    @MockK
    lateinit var providerConfigManager: ProviderManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @MockK
    lateinit var providerClaimsResolver: ProviderClaimsResolver

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var tokenEndpointClient: TokenEndpointClient

    @MockK
    lateinit var establisher: ProviderUserEstablisher

    @MockK
    lateinit var linkProviderManager: InteractiveFlowSessionLinkProviderManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var claimValueMapper: ClaimValueMapper

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    private val objectLockRepository = mockk<ObjectLockRepository>(relaxed = true)

    /**
     * The real manager over the stripe table, so a test can say which rows a link had to hold: it reads the
     * subject and the owner of the identifier values again inside the block, and a double answering for
     * `withLock` would run that block whether or not anything was ever locked.
     */
    @SpyK
    var lockManager: LockManager = LockManager(objectLockRepository, HeldStripes())

    @SpyK
    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionOAuth2ProviderManager

    private val redirectUri = URI.create("https://auth.example.com/api/v1/flow/providers/test-provider/callback")

    private val email = "user@example.com"

    /** The same address as `collected_claims` spells it, which is what a key over it names. */
    private val storedEmail = "\"user@example.com\""

    private fun createProvider(): EnabledProvider {
        return EnabledProvider(
            id = "test-provider",
            name = "Test Provider",
            userInfo = mockk<ProviderUserInfoConfig>(),
            auth = mockk<ProviderOAuth2Config>()
        )
    }

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

    @Test
    fun `authorizeWithProvider - Re-authentication rejects a provider not linked to the session user`() = runTest {
        val userId = UUID.randomUUID()
        val provider = createProvider()
        val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
        coEvery { providerConfigManager.findByIdAndCheckEnabled(provider.id) } returns provider
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.REAUTHENTICATION
        coEvery { providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, provider.id) } returns null

        val exception = assertThrows<BusinessException> {
            manager.authorizeWithProvider(session, provider.id, redirectUri)
        }

        assertEquals("flow.reauthentication.provider_not_linked", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { providerManager.setProvider(any(), any(), any()) }
    }

    /**
     * Stub the provider callback chain (token exchange, claim resolution, stored-subject lookup) up to the
     * point where the re-authentication branch is evaluated. fetchTokens is final, stubbed on the spy manager.
     *
     * [committedSince] is what a second read of the subject answers: the link path reads it again under its
     * lock, and another writer may have committed one in between. [assertedEmail] is null for a provider
     * that omits the claim.
     */
    private fun stubProviderCallbackChain(
        session: OnGoingInteractiveFlowSession,
        provider: EnabledProvider,
        subject: String,
        existingUserInfo: ProviderUserInfo?,
        committedSince: ProviderUserInfo? = existingUserInfo,
        assertedEmail: String? = email
    ): RawProviderClaims {
        val sessionProvider = mockk<InteractiveFlowSessionProvider> { every { providerId } returns provider.id }
        val tokens = mockk<ProviderOAuth2Tokens>()
        val rawUserInfo = RawProviderClaims(subject = subject, email = assertedEmail)
        coEvery { providerManager.fetchProviderOrNull(session) } returns sessionProvider
        coEvery { providerConfigManager.findByIdAndCheckEnabled(provider.id) } returns provider
        coEvery { manager.fetchTokens(provider, provider.auth, "code", redirectUri) } returns tokens
        coEvery { providerManager.buildProviderNonceOrNull(sessionProvider) } returns null
        coEvery { providerClaimsResolver.resolveClaims(provider, tokens, null) } returns rawUserInfo
        coEvery { providerClaimsManager.findByProviderAndSubject(provider, subject) } returnsMany
            listOf(existingUserInfo, committedSince)
        return rawUserInfo
    }

    @Test
    @Suppress("MaxLineLength")
    fun `signInOrSignUpUsingProvider - Re-authentication confirms the linked provider account without establishing identity`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> {
                every { this@mockk.userId } returns userId
                every { id } returns UUID.randomUUID()
            }
            val existingUserInfo = mockk<ProviderUserInfo> { every { this@mockk.userId } returns userId }
            val rawUserInfo = stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo)
            val advanced = mockk<InteractiveFlowSession>()
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.REAUTHENTICATION
            coJustRun { providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo) }
            coEvery { reauthenticationManager.markPrimaryCredentialProven(session) } returns mockk()
            coEvery { engine.completeIfNecessary(session) } returns advanced

            val result = manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )

            assertSame(advanced, result)
            coVerify { reauthenticationManager.markPrimaryCredentialProven(session) }
            coVerify(exactly = 0) { sessionManager.setAuthenticatedUserId(any(), any(), any()) }
        }

    @Test
    fun `signInOrSignUpUsingProvider - Re-authentication rejects a provider account linked to a different user`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            val existingUserInfo = mockk<ProviderUserInfo> { every { this@mockk.userId } returns UUID.randomUUID() }
            stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo)
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.REAUTHENTICATION

            val exception = assertThrows<BusinessException> {
                manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )
            }

            assertEquals("flow.reauthentication.provider_not_linked", exception.detailsId)
            assertTrue(exception.recoverable)
            coVerify(exactly = 0) { reauthenticationManager.markPrimaryCredentialProven(any()) }
            coVerify(exactly = 0) { sessionManager.setAuthenticatedUserId(any(), any(), any()) }
            coVerify(exactly = 0) { providerClaimsManager.refreshUserInfo(any(), any()) }
        }

    @Test
    @Suppress("MaxLineLength")
    fun `signInOrSignUpUsingProvider - Does not establish or switch identity when a later purpose is active after re-auth`() =
        runTest {
            // Regression for the confirm-never-establish gap: once REAUTHENTICATION has resolved and a later
            // purpose (e.g. MFA_CHALLENGE) is active, a provider round-trip must NOT fall through to the
            // establish path and switch the already-fixed session user.
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            val existingUserInfo = mockk<ProviderUserInfo>()
            stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo)
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.MFA_CHALLENGE

            val result = manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )

            assertSame(session, result)
            coVerify(exactly = 0) { sessionManager.setAuthenticatedUserId(any(), any(), any()) }
            coVerify(exactly = 0) { reauthenticationManager.markPrimaryCredentialProven(any()) }
            coVerify(exactly = 0) { providerClaimsManager.refreshUserInfo(any(), any()) }
        }

    @Test
    fun `authorizeWithProvider - Rejects a provider that is not the link target under LINK_PROVIDER`() = runTest {
        val userId = UUID.randomUUID()
        val provider = createProvider()
        val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
        coEvery { providerConfigManager.findByIdAndCheckEnabled(provider.id) } returns provider
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
        coEvery { linkProviderManager.fetchLinkProviderOrNull(session) } returns
            InteractiveFlowSessionLinkProvider(sessionId = UUID.randomUUID(), providerId = "other-provider")

        val exception = assertThrows<BusinessException> {
            manager.authorizeWithProvider(session, provider.id, redirectUri)
        }

        assertEquals("flow.link_provider.wrong_provider", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { providerManager.setProvider(any(), any(), any()) }
    }

    @Test
    fun `signInOrSignUpUsingProvider - Links the resolved provider to the fixed user`() = runTest {
        val userId = UUID.randomUUID()
        val provider = createProvider()
        val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
        val rawUserInfo = stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo = null)
        val advanced = mockk<InteractiveFlowSession>()
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
        every { uncheckedAuthConfig.identifierClaims } returns emptyList()
        coEvery { providerClaimsManager.saveUserInfo(provider, userId, null, rawUserInfo) } returns mockk()
        coEvery { engine.completeIfNecessary(session) } returns advanced

        val result = manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )

        assertSame(advanced, result)
        coVerify { providerClaimsManager.saveUserInfo(provider, userId, null, rawUserInfo) }
    }

    @Test
    fun `signInOrSignUpUsingProvider - Link is idempotent when the subject is already linked to this user`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            val existingUserInfo = mockk<ProviderUserInfo> { every { this@mockk.userId } returns userId }
            val rawUserInfo = stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo)
            val advanced = mockk<InteractiveFlowSession>()
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
            every { uncheckedAuthConfig.identifierClaims } returns emptyList()
            coJustRun { providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo) }
            coEvery { engine.completeIfNecessary(session) } returns advanced

            val result = manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )

            assertSame(advanced, result)
            coVerify { providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo) }
            coVerify(exactly = 0) { providerClaimsManager.saveUserInfo(any(), any(), any(), any()) }
        }

    @Test
    fun `signInOrSignUpUsingProvider - Link hard-fails when the subject is linked to another account`() = runTest {
        val userId = UUID.randomUUID()
        val provider = createProvider()
        val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
        val existingUserInfo = mockk<ProviderUserInfo> { every { this@mockk.userId } returns UUID.randomUUID() }
        stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo)
        coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
        every { uncheckedAuthConfig.identifierClaims } returns emptyList()

        val exception = assertThrows<BusinessException> {
            manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )
        }

        assertEquals("flow.link_provider.subject_conflict", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { providerClaimsManager.saveUserInfo(any(), any(), any(), any()) }
    }

    @Test
    fun `signInOrSignUpUsingProvider - Link hard-fails when an identifier value is owned by another account`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo = null)
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            every { claimValueMapper.toEntity(email) } returns storedEmail
            coEvery {
                userManager.findTakenIdentifierClaimIdOrNull(
                    userId,
                    listOf(OpenIdConnectClaimId.EMAIL),
                    mapOf(OpenIdConnectClaimId.EMAIL to storedEmail)
                )
            } returns OpenIdConnectClaimId.EMAIL

            val exception = assertThrows<BusinessException> {
                manager.signInOrSignUpUsingProvider(
                    session, provider.id, redirectUri, authorizeCode = "code",
                    observedRequest = observedRequestOf()
                )
            }

            assertEquals("flow.link_provider.identifier_conflict", exception.detailsId)
            assertFalse(exception.recoverable)
            coVerify { objectLockRepository.lock(LockKey.IdentifierValue(storedEmail).stripe) }
            coVerify(exactly = 0) { providerClaimsManager.saveUserInfo(any(), any(), any(), any()) }
        }

    @Test
    fun `signInOrSignUpUsingProvider - Link asks the conflict against the session's own account`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            val rawUserInfo = stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo = null)
            val advanced = mockk<InteractiveFlowSession>()
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            every { claimValueMapper.toEntity(email) } returns storedEmail
            coEvery { userManager.findTakenIdentifierClaimIdOrNull(userId, any(), any()) } returns null
            coEvery { providerClaimsManager.saveUserInfo(provider, userId, null, rawUserInfo) } returns mockk()
            coEvery { engine.completeIfNecessary(session) } returns advanced

            val result = manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )

            assertSame(advanced, result)
            // Naming the session's own account is what exempts an address it already holds — the end-user
            // linking a provider to the account they opened with that same address. Which of the rows found
            // count as its own is UserManagerTest's.
            coVerify {
                userManager.findTakenIdentifierClaimIdOrNull(
                    userId,
                    listOf(OpenIdConnectClaimId.EMAIL),
                    mapOf(OpenIdConnectClaimId.EMAIL to storedEmail)
                )
            }
            coVerify { providerClaimsManager.saveUserInfo(provider, userId, null, rawUserInfo) }
        }

    @Test
    fun `signInOrSignUpUsingProvider - Link checks the identifier claims the provider does assert`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo = null)
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
            every { uncheckedAuthConfig.identifierClaims } returns listOf(
                OpenIdConnectClaimId.EMAIL, OpenIdConnectClaimId.PHONE_NUMBER
            )
            every { claimValueMapper.toEntity(email) } returns storedEmail
            coEvery {
                userManager.findTakenIdentifierClaimIdOrNull(userId, any(), any())
            } returns OpenIdConnectClaimId.EMAIL

            val exception = assertThrows<BusinessException> {
                manager.signInOrSignUpUsingProvider(
                    session, provider.id, redirectUri, authorizeCode = "code",
                    observedRequest = observedRequestOf()
                )
            }

            assertEquals("flow.link_provider.identifier_conflict", exception.detailsId)
            // The number the provider is silent about is no reason to skip the address it did assert, and
            // every configured claim is still searched: an account holding that address under either of
            // them owns it.
            coVerify {
                userManager.findTakenIdentifierClaimIdOrNull(
                    userId,
                    listOf(OpenIdConnectClaimId.EMAIL, OpenIdConnectClaimId.PHONE_NUMBER),
                    mapOf(OpenIdConnectClaimId.EMAIL to storedEmail)
                )
            }
            coVerify(exactly = 0) { providerClaimsManager.saveUserInfo(any(), any(), any(), any()) }
        }

    @Test
    fun `signInOrSignUpUsingProvider - Link hard-fails when another account took the subject under the lock`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            val takenSince = mockk<ProviderUserInfo> { every { this@mockk.userId } returns UUID.randomUUID() }
            stubProviderCallbackChain(
                session, provider, "sub-123", existingUserInfo = null, committedSince = takenSince
            )
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
            every { uncheckedAuthConfig.identifierClaims } returns emptyList()

            val exception = assertThrows<BusinessException> {
                manager.signInOrSignUpUsingProvider(
                    session, provider.id, redirectUri, authorizeCode = "code",
                    observedRequest = observedRequestOf()
                )
            }

            assertEquals("flow.link_provider.subject_conflict", exception.detailsId)
            coVerify(exactly = 0) { providerClaimsManager.saveUserInfo(any(), any(), any(), any()) }
        }

    @Test
    fun `signInOrSignUpUsingProvider - Link refreshes when this account took the subject under the lock`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            val linkedSince = mockk<ProviderUserInfo> { every { this@mockk.userId } returns userId }
            val rawUserInfo = stubProviderCallbackChain(
                session, provider, "sub-123", existingUserInfo = null, committedSince = linkedSince
            )
            val advanced = mockk<InteractiveFlowSession>()
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
            every { uncheckedAuthConfig.identifierClaims } returns emptyList()
            coJustRun { providerClaimsManager.refreshUserInfo(linkedSince, rawUserInfo) }
            coEvery { engine.completeIfNecessary(session) } returns advanced

            val result = manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )

            assertSame(advanced, result)
            coVerify { providerClaimsManager.refreshUserInfo(linkedSince, rawUserInfo) }
            coVerify(exactly = 0) { providerClaimsManager.saveUserInfo(any(), any(), any(), any()) }
        }

    @Test
    fun `signInOrSignUpUsingProvider - Link holds the identifier values its conflict check compares`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            val rawUserInfo = stubProviderCallbackChain(session, provider, "sub-123", existingUserInfo = null)
            val advanced = mockk<InteractiveFlowSession>()
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            every { claimValueMapper.toEntity(email) } returns storedEmail
            coEvery { userManager.findTakenIdentifierClaimIdOrNull(userId, any(), any()) } returns null
            coEvery { providerClaimsManager.saveUserInfo(provider, userId, null, rawUserInfo) } returns mockk()
            coEvery { engine.completeIfNecessary(session) } returns advanced

            val result = manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )

            assertSame(advanced, result)
            // The address as well as the subject: the promotion this has to be excluded from commits an
            // account holding that address, and the key it takes names the address.
            coVerify { objectLockRepository.lock(LockKey.ProviderSubject(provider.id, "sub-123").stripe) }
            coVerify { objectLockRepository.lock(LockKey.IdentifierValue(storedEmail).stripe) }
        }

    @Test
    fun `signInOrSignUpUsingProvider - Link holds no identifier value when the provider asserts none`() =
        runTest {
            val userId = UUID.randomUUID()
            val provider = createProvider()
            val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
            val rawUserInfo = stubProviderCallbackChain(
                session, provider, "sub-123", existingUserInfo = null, assertedEmail = null
            )
            val advanced = mockk<InteractiveFlowSession>()
            coEvery { engine.currentPurposeOrNull(session) } returns InteractiveFlowPurpose.LINK_PROVIDER
            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            coEvery { providerClaimsManager.saveUserInfo(provider, userId, null, rawUserInfo) } returns mockk()
            coEvery { engine.completeIfNecessary(session) } returns advanced

            val result = manager.signInOrSignUpUsingProvider(
                session, provider.id, redirectUri, authorizeCode = "code",
                observedRequest = observedRequestOf()
            )

            assertSame(advanced, result)
            // userManager is left unstubbed: a provider asserting none of the configured identifier claims
            // offers nothing to compare, so reaching the write is proof it was never asked — and a key over
            // a check that did not run would make a promotion wait for nothing. One row, and it is the
            // subject's: naming a stripe that must not be taken would say nothing about a key built from
            // some other spelling.
            coVerify(exactly = 1) { objectLockRepository.lock(any()) }
            coVerify { objectLockRepository.lock(LockKey.ProviderSubject(provider.id, "sub-123").stripe) }
        }
}
