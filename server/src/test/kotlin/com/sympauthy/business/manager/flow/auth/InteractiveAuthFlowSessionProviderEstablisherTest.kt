package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.TakenIdentifier
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.data.repository.ObjectLockRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveAuthFlowSessionProviderEstablisherTest {

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    /**
     * The real manager over stripes nothing else takes: what the tests below turn on is that the block
     * runs and that the subject is read again inside it, and a double answering for `withLock` would run
     * the block whether or not anything was ever locked.
     */
    @SpyK
    var lockManager: LockManager = LockManager(mockk<ObjectLockRepository>(relaxed = true), HeldStripes())

    @InjectMockKs
    lateinit var establisher: InteractiveAuthFlowSessionProviderEstablisher

    private fun createProvider(): EnabledProvider {
        return EnabledProvider(
            id = "test-provider",
            name = "Test Provider",
            userInfo = mockk<ProviderUserInfoConfig>(),
            auth = mockk<ProviderOAuth2Config>()
        )
    }

    private val sessionId = UUID.randomUUID()
    /** The account a refusal names as holding the value, which reaches an operator and never the person refused. */
    private val ownerId = UUID.randomUUID()


    private fun createUser(sessionId: UUID? = null): User {
        return User(
            id = UUID.randomUUID(),
            status = UserStatus.ENABLED,
            creationDate = LocalDateTime.now(),
            sessionId = sessionId
        )
    }

    /** The shared rule answering that nothing committed holds what the provider asserts. */
    private fun identifierValues(valuesByClaimId: Map<String, String>, takenClaimId: String? = null) {
        every { collectedClaimManager.getIdentifierValuesIn(any()) } returns valuesByClaimId
        coEvery {
            userManager.findTakenIdentifierOrNull(null, any(), valuesByClaimId)
        } returns takenClaimId?.let { TakenIdentifier(claimId = it, userId = ownerId) }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `createOrAssociateUserWithProviderUserInfo - Merge when merging enabled and user exists with matching identifier claims`() =
        runTest {
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(
                subject = "sub-123",
                email = "user@example.com"
            )
            val existingUser = createUser()
            val emailClaim = mockk<Claim>()

            every { uncheckedAuthConfig.userMergingEnabled } returns true
            coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-123") } returns null
            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
            coEvery { userManager.findByIdentifierClaims(mapOf("email" to "user@example.com")) } returns existingUser
            coJustRun { providerClaimsManager.saveUserInfo(provider, existingUser.id, null, providerUserInfo) }

            val result = establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)

            assertFalse(result.created)
            assertSame(existingUser, result.user)
            coVerify { providerClaimsManager.saveUserInfo(provider, existingUser.id, null, providerUserInfo) }
        }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Create new user when merging enabled but no existing user`() =
        runTest {
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(
                subject = "sub-123",
                email = "new@example.com"
            )
            val newUser = createUser(sessionId = sessionId)
            val emailClaim = mockk<Claim>()

            every { uncheckedAuthConfig.userMergingEnabled } returns true
            coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-123") } returns null
            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
            coEvery { userManager.findByIdentifierClaims(mapOf("email" to "new@example.com")) } returns null
            identifierValues(mapOf("email" to "\"new@example.com\""))
            coEvery { userManager.createUser(sessionId) } returns newUser
            coJustRun { collectedClaimManager.update(newUser, any()) }
            coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, sessionId, providerUserInfo) }

            val result = establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)

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
        coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-123") } returns null
        every { uncheckedAuthConfig.identifierClaims } returns listOf(
            OpenIdConnectClaimId.EMAIL,
            OpenIdConnectClaimId.PHONE_NUMBER
        )
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.PHONE_NUMBER) } returns phoneClaim
        coEvery {
            userManager.findByIdentifierClaims(mapOf("email" to "user@example.com", "phone_number" to "+33612345678"))
        } returns existingUser
        coJustRun { providerClaimsManager.saveUserInfo(provider, existingUser.id, null, providerUserInfo) }

        val result = establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)

        assertFalse(result.created)
        assertSame(existingUser, result.user)
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Create with multiple identifier claims when no existing user`() =
        runTest {
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(
                subject = "sub-123",
                email = "new@example.com",
                phoneNumber = "+33612345678"
            )
            val newUser = createUser(sessionId = sessionId)
            val emailClaim = mockk<Claim>()
            val phoneClaim = mockk<Claim>()

            every { uncheckedAuthConfig.userMergingEnabled } returns true
            coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-123") } returns null
            every { uncheckedAuthConfig.identifierClaims } returns listOf(
                OpenIdConnectClaimId.EMAIL,
                OpenIdConnectClaimId.PHONE_NUMBER
            )
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.PHONE_NUMBER) } returns phoneClaim
            coEvery {
                userManager.findByIdentifierClaims(
                    mapOf(
                        "email" to "new@example.com",
                        "phone_number" to "+33612345678"
                    )
                )
            } returns null
            identifierValues(
                mapOf("email" to "\"new@example.com\"", "phone_number" to "\"+33612345678\"")
            )
            coEvery { userManager.createUser(sessionId) } returns newUser
            coJustRun { collectedClaimManager.update(newUser, any()) }
            coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, sessionId, providerUserInfo) }

            val result = establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)

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
    fun `createOrAssociateUserWithProviderUserInfo - Throw when identifier claim not provided by provider`() =
        runTest {
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(
                subject = "sub-123",
                email = null
            )

            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)

            val exception = assertThrows<BusinessException> {
                establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)
            }

            assertEquals("user.create_with_provider.missing_identifier_claim", exception.detailsId)
            assertEquals("email", exception.values["claim"])
        }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Throw when identifier claim not configured`() =
        runTest {
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(
                subject = "sub-123",
                email = "user@example.com"
            )

            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns null

            val exception = assertThrows<BusinessException> {
                establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)
            }

            assertEquals("user.create_with_provider.missing_identifier_claim_config", exception.detailsId)
            assertEquals("email", exception.values["claim"])
        }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Create new user when merging disabled and no existing user`() =
        runTest {
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(
                subject = "sub-123",
                email = "new@example.com"
            )
            val newUser = createUser(sessionId = sessionId)
            val emailClaim = mockk<Claim>()

            every { uncheckedAuthConfig.userMergingEnabled } returns false
            coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-123") } returns null
            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
            identifierValues(mapOf("email" to "\"new@example.com\""))
            coEvery { userManager.createUser(sessionId) } returns newUser
            coJustRun { collectedClaimManager.update(newUser, any()) }
            coJustRun { providerClaimsManager.saveUserInfo(provider, newUser.id, sessionId, providerUserInfo) }

            val result = establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)

            assertTrue(result.created)
            assertSame(newUser, result.user)
            coVerify {
                collectedClaimManager.update(newUser, withArg { updates ->
                    assertEquals(1, updates.size)
                    assertEquals(emailClaim, updates[0].claim)
                    assertEquals(Optional.of("new@example.com"), updates[0].value)
                })
            }
            coVerify { providerClaimsManager.saveUserInfo(provider, newUser.id, sessionId, providerUserInfo) }
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
        coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-123") } returns null
        every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
        every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
        identifierValues(mapOf("email" to "\"existing@example.com\""), takenClaimId = "email")

        val exception = assertThrows<BusinessException> {
            establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)
        }

        assertEquals("user.create_with_provider.existing_user", exception.detailsId)
        assertEquals("email", exception.values["claim"])
        assertEquals(ownerId.toString(), exception.values["userId"])
        coVerify(exactly = 0) { userManager.createUser(any()) }
    }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Refuse when one asserted value is owned and the rest are free`() =
        runTest {
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(
                subject = "sub-123",
                email = "taken@example.com",
                phoneNumber = "+33612345678"
            )
            val emailClaim = mockk<Claim>()
            val phoneClaim = mockk<Claim>()

            every { uncheckedAuthConfig.userMergingEnabled } returns true
            coEvery { providerClaimsManager.findByProviderAndSubject(provider, "sub-123") } returns null
            every { uncheckedAuthConfig.identifierClaims } returns listOf(
                OpenIdConnectClaimId.EMAIL,
                OpenIdConnectClaimId.PHONE_NUMBER
            )
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns emailClaim
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.PHONE_NUMBER) } returns phoneClaim
            // No account matches both, which is what the resolving read answers and why merging finds none.
            coEvery {
                userManager.findByIdentifierClaims(
                    mapOf("email" to "taken@example.com", "phone_number" to "+33612345678")
                )
            } returns null
            val asserted = mapOf(
                "email" to "\"taken@example.com\"",
                "phone_number" to "\"+33612345678\""
            )
            every { collectedClaimManager.getIdentifierValuesIn(any()) } returns asserted
            coEvery {
                userManager.findTakenIdentifierOrNull(
                    null,
                    listOf(OpenIdConnectClaimId.EMAIL, OpenIdConnectClaimId.PHONE_NUMBER),
                    asserted
                )
            } returns TakenIdentifier(claimId = OpenIdConnectClaimId.EMAIL, userId = ownerId)

            val exception = assertThrows<BusinessException> {
                establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)
            }

            // The account created over that address would have died at its own promotion instead.
            assertEquals("user.create_with_provider.existing_user", exception.detailsId)
            assertEquals(OpenIdConnectClaimId.EMAIL, exception.values["claim"])
            assertEquals(ownerId.toString(), exception.values["userId"])
            coVerify(exactly = 0) { userManager.createUser(any()) }
        }

    @Test
    fun `createOrAssociateUserWithProviderUserInfo - Throw when the subject was linked while the callback ran`() =
        runTest {
            val provider = createProvider()
            val providerUserInfo = RawProviderClaims(
                subject = "sub-123",
                email = "new@example.com"
            )

            every { uncheckedAuthConfig.identifierClaims } returns listOf(OpenIdConnectClaimId.EMAIL)
            every { claimManager.findByIdOrNull(OpenIdConnectClaimId.EMAIL) } returns mockk<Claim>()
            coEvery {
                providerClaimsManager.findByProviderAndSubject(provider, "sub-123")
            } returns mockk<ProviderUserInfo>()

            val exception = assertThrows<BusinessException> {
                establisher.createOrAssociateUserWithProviderUserInfo(sessionId, provider, providerUserInfo)
            }

            assertEquals("user.create_with_provider.subject_taken", exception.detailsId)
            assertTrue(exception.recoverable)
            coVerify(exactly = 0) { userManager.createUser(any()) }
            coVerify(exactly = 0) { providerClaimsManager.saveUserInfo(any(), any(), any(), any()) }
        }
}
