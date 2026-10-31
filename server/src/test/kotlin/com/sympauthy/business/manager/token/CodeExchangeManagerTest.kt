package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.manager.auth.oauth2.PkceManager
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.IssuedTokens
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.data.repository.ObjectLockRepository
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
import com.sympauthy.monitoring.telemetry.LockMetrics
import com.sympauthy.monitoring.telemetry.TokenMetrics
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*

@ExtendWith(MockKExtension::class)
class CodeExchangeManagerTest {

    @MockK
    lateinit var authorizationCodeManager: AuthorizationCodeManager

    @MockK
    lateinit var pkceManager: PkceManager

    private val userManager = mockk<UserManager>()

    /**
     * The real manager behind a spy rather than a mock: whether a grant is live, opened for the client
     * presenting it and opened for a finished account are functions of the grant, the client and the account
     * alone, so every scenario runs them instead of stubbing an answer to them.
     */
    @SpyK
    var grantManager = GrantManager(mockk(), mockk(), mockk(), userManager, mockk(), TokenMetrics(OpenTelemetry.noop()))

    private val objectLockRepository = mockk<ObjectLockRepository>(relaxed = true)

    /** The real manager over the stripe table, so a test can say which row was taken before what. */
    var lockManager: LockManager = LockManager(objectLockRepository, HeldStripes(), LockMetrics(OpenTelemetry.noop()))

    @MockK
    lateinit var accessTokenGenerator: AccessTokenGenerator

    @MockK
    lateinit var refreshTokenGenerator: RefreshTokenGenerator

    @MockK
    lateinit var idTokenGenerator: IdTokenGenerator

    @MockK
    lateinit var claimAclManager: ClaimAclManager

    @SpyK
    var customSpanTracer = CustomSpanTracer(OpenTelemetry.noop())

    @SpyK
    @InjectMockKs
    lateinit var manager: CodeExchangeManager

    private fun mockClientWithGrantTypes(vararg grantTypes: GrantType): Client {
        return mockk {
            every { supportsGrantType(any()) } answers { grantTypes.contains(firstArg()) }
            every { audience } returns mockk {
                every { id } returns "test-audience"
            }
        }
    }

    /** A code naming a grant of its own, live for another minute unless [expirationDate] says otherwise. */
    private fun code(expirationDate: LocalDateTime = now().plusMinutes(1)) = AuthorizationCode(
        grantId = UUID.randomUUID(),
        code = "code",
        codeChallenge = "challenge",
        codeChallengeMethod = CodeChallengeMethod.S256,
        nonce = "nonce",
        creationDate = expirationDate.minusMinutes(1),
        expirationDate = expirationDate
    )

    /** The grant [code] names as the caller read it, before the lock: it is asked for nothing but its account. */
    private fun grantReadBeforeTheLock(userId: UUID): Grant = mockk { every { this@mockk.userId } returns userId }

    /** Stubs [code] as naming [grant] once the lock is held, and [userId] as an account a sign-up finished. */
    private fun stubExchangeableCode(code: AuthorizationCode, userId: UUID, grant: Grant) {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk()
        coEvery { authorizationCodeManager.fetchGrant(code) } returns grant
    }

    /** The doors every token of one response comes through. */
    private val doors = mockk<ClaimAclTokenDoors>()

    /**
     * Stubs the doors of a token issued under [grant] to [client], bound where [dpopJkt] is set, as [doors]: a
     * generator handed any other set was handed doors the response did not build for it.
     */
    private fun stubDoors(grant: Grant, client: Client, dpopJkt: String?) {
        every { grant.consentedScopes } returns emptyList()
        every { grant.grantedScopes } returns emptyList()
        every { grant.clientScopes } returns emptyList()
        every { grant.authenticationDate } returns null
        val context = ClaimAclContext.of(grant, client, dpopJkt)
        every { claimAclManager.getTokenDoors(context, any()) } returns doors
    }

    private fun mockGrant(
        revoked: Boolean = false,
        expired: Boolean = false,
        grantType: GrantType? = null
    ): Grant = mockk {
        every { this@mockk.revoked } returns revoked
        if (!revoked) every { this@mockk.expired } returns expired
        grantType?.let { every { this@mockk.grantType } returns it }
    }

    @Test
    fun `exchangeCode - Throws internal for a grant opened for no user, and spends nothing`() = runTest {
        val grant = mockk<Grant> {
            every { id } returns UUID.randomUUID()
            every { userId } returns null
        }

        val exception = assertThrows<BusinessException> {
            manager.exchangeCode(code(), grant, mockk())
        }

        assertEquals("code.missing_user", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
        coVerify(exactly = 0) { authorizationCodeManager.markExchanged(any()) }
    }

    @Test
    fun `exchangeCode - Generate all tokens under the grant the code names`() = runTest {
        val userId = UUID.randomUUID()
        val code = code()
        val client = mockClientWithGrantTypes(*GrantType.entries.toTypedArray())
        val audience = client.audience
        val grant = mockGrant(grantType = GrantType.AUTHORIZATION_CODE)
        val accessToken = mockk<EncodedAuthenticationToken>()
        val refreshToken = mockk<EncodedAuthenticationToken>()
        val idToken = mockk<EncodedAuthenticationToken>()

        stubExchangeableCode(code, userId, grant)
        coEvery { authorizationCodeManager.markExchanged(code) } returns true
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.AUTHORIZATION_CODE, null)
        } returns accessToken
        coEvery {
            refreshTokenGenerator.generateRefreshToken(grant, audience, GrantType.AUTHORIZATION_CODE, null)
        } returns refreshToken
        coEvery {
            idTokenGenerator.generateIdToken(
                grant,
                match { it.id == "test-audience" },
                accessToken,
                doors,
                GrantType.AUTHORIZATION_CODE,
                "nonce"
            )
        } returns idToken

        val result = manager.exchangeCode(code, grantReadBeforeTheLock(userId), client)

        assertSame(accessToken, result.accessToken)
        assertSame(refreshToken, result.refreshToken)
        assertSame(idToken, result.idToken)
        verify(exactly = 1) { claimAclManager.getTokenDoors(any(), any()) }
        coVerifyOrder {
            objectLockRepository.lock(LockKey.User(userId).stripe)
            authorizationCodeManager.fetchGrant(code)
            authorizationCodeManager.markExchanged(code)
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.AUTHORIZATION_CODE, null)
        }
    }

    @Test
    fun `exchangeCode - Refuses an account no sign-up finished, and spends nothing`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeCode(code(), grantReadBeforeTheLock(userId), mockk())
        }

        assertEquals("grant.invalid_user", exception.detailsId)
        coVerify(exactly = 0) { authorizationCodeManager.markExchanged(any()) }
    }

    @Test
    fun `exchangeCode - Refuses as expired a code the cleaner collected with its grant meanwhile`() = runTest {
        val userId = UUID.randomUUID()
        val code = code()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk()
        coEvery {
            authorizationCodeManager.fetchGrant(code)
        } throws oauth2ExceptionOf(INVALID_GRANT, "token.expired")

        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeCode(code, grantReadBeforeTheLock(userId), mockk())
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
        coVerify(exactly = 0) { authorizationCodeManager.markExchanged(any()) }
        coVerify(exactly = 0) { grantManager.revokeGrant(any(), any(), any()) }
    }

    @Test
    fun `exchangeCode - Revokes the grant the code names and issues none for a code already exchanged`() =
        runTest {
            val userId = UUID.randomUUID()
            val code = code()
            val grant = mockk<Grant>()
            stubExchangeableCode(code, userId, grant)
            coEvery { authorizationCodeManager.markExchanged(code) } returns false
            coJustRun { grantManager.revokeGrant(grant, GrantRevokedBy.AUTHORIZATION_CODE_REPLAYED, null) }

            val exception = assertThrows<OAuth2Exception> {
                manager.exchangeCode(code, grantReadBeforeTheLock(userId), mockk())
            }

            assertEquals(INVALID_GRANT, exception.errorCode)
            assertEquals("code.already_exchanged", exception.detailsId)
            assertEquals("description.oauth2.replay", exception.descriptionId)
            coVerify(exactly = 1) {
                grantManager.revokeGrant(grant, GrantRevokedBy.AUTHORIZATION_CODE_REPLAYED, null)
            }
            coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { refreshTokenGenerator.generateRefreshToken(any(), any(), any(), any()) }
        }

    @Test
    fun `exchangeCode - Refuses a code whose grant was revoked before it was exchanged`() = runTest {
        val userId = UUID.randomUUID()
        val code = code()
        stubExchangeableCode(code, userId, mockGrant(revoked = true))
        coEvery { authorizationCodeManager.markExchanged(code) } returns true

        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeCode(code, grantReadBeforeTheLock(userId), mockk())
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.revoked", exception.detailsId)
        coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { grantManager.revokeGrant(any(), any(), any()) }
    }

    @Test
    fun `exchangeCode - Refuses a code whose grant is past its lifetime`() = runTest {
        val userId = UUID.randomUUID()
        val code = code()
        stubExchangeableCode(code, userId, mockGrant(expired = true))
        coEvery { authorizationCodeManager.markExchanged(code) } returns true

        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeCode(code, grantReadBeforeTheLock(userId), mockk())
        }

        assertEquals("grant.expired", exception.detailsId)
        assertEquals("description.grant.expired", exception.descriptionId)
        coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
    }

    private fun mockCode(): AuthorizationCode = mockk {
        every { codeChallenge } returns "stored-challenge"
        every { codeChallengeMethod } returns CodeChallengeMethod.S256
    }

    @Test
    fun `exchange - Refuses the code as the check does, before reading the verifier`() = runTest {
        val client = mockk<Client>()
        coEvery { authorizationCodeManager.findByCodeOrNull("the-code") } returns null
        coEvery {
            authorizationCodeManager.checkCanExchange(null, client)
        } throws oauth2ExceptionOf(INVALID_GRANT, "token.expired")

        val exception = assertThrows<OAuth2Exception> {
            manager.exchange(client, TokenRequest.AuthorizationCode("the-code", "the-verifier"), null)
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
        verify(exactly = 0) { pkceManager.verifyCodeVerifier(any(), any(), any()) }
        coVerify(exactly = 0) { manager.exchangeCode(any(), any(), any(), any()) }
    }

    @Test
    fun `exchange - Throws invalid_grant when PKCE verification fails`() = runTest {
        val client = mockk<Client>()
        val code = mockCode()
        coEvery { authorizationCodeManager.findByCodeOrNull("the-code") } returns code
        coEvery { authorizationCodeManager.checkCanExchange(code, client) } returns
            (code to mockk<Grant> { every { sessionId } returns null })
        every {
            pkceManager.verifyCodeVerifier("wrong-verifier", "stored-challenge", CodeChallengeMethod.S256)
        } throws businessExceptionOf(detailsId = "token.pkce.invalid_code_verifier")

        val exception = assertThrows<OAuth2Exception> {
            manager.exchange(client, TokenRequest.AuthorizationCode("the-code", "wrong-verifier"), null)
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.pkce.invalid_code_verifier", exception.detailsId)
        coVerify(exactly = 0) { manager.exchangeCode(any(), any(), any(), any()) }
    }

    @Test
    fun `exchange - Issues the tokens of the grant the code names, bound to the key`() = runTest {
        val client = mockk<Client>()
        val code = mockCode()
        val grant = mockk<Grant> { every { sessionId } returns null }
        val tokens = IssuedTokens(mockk())
        coEvery { authorizationCodeManager.findByCodeOrNull("the-code") } returns code
        coEvery { authorizationCodeManager.checkCanExchange(code, client) } returns (code to grant)
        every {
            pkceManager.verifyCodeVerifier("the-verifier", "stored-challenge", CodeChallengeMethod.S256)
        } just runs
        coEvery { manager.exchangeCode(code, grant, client, "a-jkt") } returns tokens

        val result = manager.exchange(client, TokenRequest.AuthorizationCode("the-code", "the-verifier"), "a-jkt")

        assertSame(tokens, result)
    }
}
