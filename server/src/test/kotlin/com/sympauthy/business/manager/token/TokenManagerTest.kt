package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.auth.ActorGrantValidator
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.PUBLIC_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.token.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.token.AuthenticationTokenType.REFRESH
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.monitoring.telemetry.TokenMetrics
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class TokenManagerTest {

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var actorGrantValidator: ActorGrantValidator

    /**
     * The real manager behind a spy rather than a mock: whether a grant is live is a function of the grant
     * alone, so every scenario runs it instead of stubbing an answer to it.
     */
    @SpyK
    var grantManager = GrantManager(mockk(), mockk(), mockk(), mockk(), mockk(), TokenMetrics(OpenTelemetry.noop()))

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var tokenMapper: AuthenticationTokenMapper

    @SpyK
    @InjectMockKs
    lateinit var tokenManager: TokenManager

    @Test
    fun `findById - Returns the token with the grant it was issued under`() = runTest {
        val tokenId = UUID.randomUUID()
        val grantId = UUID.randomUUID()
        val entity = mockk<AuthenticationTokenEntity> { every { this@mockk.grantId } returns grantId }
        val grant = mockk<Grant>()
        val token = mockk<AuthenticationToken>()
        coEvery { tokenRepository.findById(tokenId) } returns entity
        coEvery { grantManager.findByIdOrNull(grantId) } returns grant
        every { tokenMapper.toToken(entity, grant) } returns token

        assertSame(token, tokenManager.findById(tokenId))
    }

    @Test
    fun `findById - Returns null when the grant and the token were collected between the two reads`() = runTest {
        val tokenId = UUID.randomUUID()
        val grantId = UUID.randomUUID()
        val entity = mockk<AuthenticationTokenEntity> { every { this@mockk.grantId } returns grantId }
        coEvery { tokenRepository.findById(tokenId) } returns entity andThen null
        coEvery { grantManager.findByIdOrNull(grantId) } returns null

        assertNull(tokenManager.findById(tokenId))
        coVerify(exactly = 0) { tokenMapper.toToken(any(), any()) }
    }

    @Test
    fun `findById - Hands the mapper the missing grant of a token still there`() = runTest {
        val tokenId = UUID.randomUUID()
        val grantId = UUID.randomUUID()
        val entity = mockk<AuthenticationTokenEntity> { every { this@mockk.grantId } returns grantId }
        val failure = BusinessException(recoverable = false, detailsId = "mapper.authentication_token.invalid_property")
        coEvery { tokenRepository.findById(tokenId) } returns entity
        coEvery { grantManager.findByIdOrNull(grantId) } returns null
        every { tokenMapper.toToken(entity, null) } throws failure

        val exception = assertThrows<BusinessException> { tokenManager.findById(tokenId) }

        assertSame(failure, exception)
    }

    @Test
    fun `getAuthenticationToken - Throws if token id is not a valid UUID`() = runTest {
        val decodedToken = DecodedJwt(id = "not-a-uuid", subject = null, keyId = null)
        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("token.invalid_token_id", exception.detailsId)
    }

    @Test
    fun `getAuthenticationToken - Throws if token is missing from database`() = runTest {
        val tokenId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = null, keyId = null)

        coEvery { tokenManager.findById(tokenId) } returns null

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("token.invalid_token_id", exception.detailsId)
    }

    @Test
    fun `getAuthenticationToken - Throws if the grant of the token is revoked`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = userId.toString(), keyId = null)
        val grant = mockk<Grant> {
            every { this@mockk.userId } returns userId
            every { revoked } returns true
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("grant.revoked", exception.detailsId)
        coVerify(exactly = 0) { actorGrantValidator.validateActorGrant(any()) }
    }

    @Test
    fun `getAuthenticationToken - Throws if token subject does not match the user of its grant`() = runTest {
        val tokenId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = "not-the-same", keyId = null)
        val grant = mockk<Grant> {
            every { userId } returns UUID.randomUUID()
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("token.invalid_token_id", exception.detailsId)
    }

    @Test
    fun `getAuthenticationToken - Throws if the grant of the token is past its lifetime`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = userId.toString(), keyId = null)
        val grant = mockk<Grant> {
            every { this@mockk.userId } returns userId
            every { revoked } returns false
            every { expired } returns true
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.expired", exception.detailsId)
        assertEquals("description.grant.expired", exception.descriptionId)
    }

    @Test
    fun `getAuthenticationToken - Return token info from database if validated`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = userId.toString(), keyId = null)
        val grant = mockk<Grant> {
            every { this@mockk.userId } returns userId
            every { revoked } returns false
            every { expired } returns false
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token
        coJustRun { actorGrantValidator.validateActorGrant(grant) }

        assertSame(token, tokenManager.getAuthenticationToken(decodedToken))
    }

    @Test
    fun `getAuthenticationToken - Takes the client as the subject of a grant naming no user`() = runTest {
        val tokenId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = "client", keyId = null)
        val grant = mockk<Grant> {
            every { userId } returns null
            every { clientId } returns "client"
            every { revoked } returns false
            every { expired } returns false
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token
        coJustRun { actorGrantValidator.validateActorGrant(grant) }

        assertSame(token, tokenManager.getAuthenticationToken(decodedToken))
    }

    @Test
    fun `getAuthenticationToken - Throws if the actor grant is no longer live`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = userId.toString(), keyId = null)
        val grant = mockk<Grant> {
            every { this@mockk.userId } returns userId
            every { revoked } returns false
            every { expired } returns false
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token
        coEvery {
            actorGrantValidator.validateActorGrant(grant)
        } throws oauth2ExceptionOf(INVALID_GRANT, "grant.actor_revoked")

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("grant.actor_revoked", exception.detailsId)
    }

    @Test
    fun `revokeTokenByEncodedToken - Does not throw when token cannot be decoded`() = runTest {
        val client = mockk<Client>()
        every { jwtManager.getKeyIdOrNull("invalid") } returns null

        tokenManager.revokeTokenByEncodedToken(client, "invalid")

        coVerify(exactly = 0) { grantManager.revokeGrant(any(), any(), any()) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Does not revoke when token belongs to another client`() = runTest {
        val tokenId = UUID.randomUUID()
        val client = mockk<Client> { every { id } returns "client-a" }
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "client-b" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "token") } returns decodedToken
        coEvery { tokenManager.findById(tokenId) } returns token

        tokenManager.revokeTokenByEncodedToken(client, "token")

        coVerify(exactly = 0) { grantManager.revokeGrant(any(), any(), any()) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Revokes the grant of an access token`() = runTest {
        val tokenId = UUID.randomUUID()
        val client = mockk<Client> { every { id } returns "client-a" }
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "client-a" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "token") } returns decodedToken
        coEvery { tokenManager.findById(tokenId) } returns token
        coJustRun { grantManager.revokeGrant(grant, GrantRevokedBy.CLIENT, null) }

        tokenManager.revokeTokenByEncodedToken(client, "token")

        coVerify(exactly = 1) { grantManager.revokeGrant(grant, GrantRevokedBy.CLIENT, null) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Revokes the grant of a refresh token`() = runTest {
        val tokenId = UUID.randomUUID()
        val client = mockk<Client> { every { id } returns "client-a" }
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "client-a" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("token") } returns REFRESH_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.findById(tokenId) } returns token
        coJustRun { grantManager.revokeGrant(grant, GrantRevokedBy.CLIENT, null) }

        tokenManager.revokeTokenByEncodedToken(client, "token")

        coVerify(exactly = 0) { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, any()) }
        coVerify(exactly = 1) { grantManager.revokeGrant(grant, GrantRevokedBy.CLIENT, null) }
    }

    @Test
    fun `introspectToken - Returns token for active access token`() = runTest {
        val client = client("test-client")
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val grant = grant("test-client", AUDIENCE)
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertSame(token, result)
    }

    @Test
    fun `introspectToken - Returns token for active refresh token`() = runTest {
        val client = client("test-client")
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val grant = grant("test-client", AUDIENCE)
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns REFRESH_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertSame(token, result)
    }

    @Test
    fun `introspectToken - Returns null for a token of another type than the one asked for`() = runTest {
        val client = mockk<Client>()
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val token = mockk<AuthenticationToken> { every { type } returns REFRESH }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns REFRESH_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = ACCESS)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null for malformed token`() = runTest {
        val client = mockk<Client>()
        every { jwtManager.getKeyIdOrNull("malformed") } returns null

        val result = tokenManager.introspectToken(client, "malformed", type = null)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null for expired token`() = runTest {
        val client = mockk<Client>()
        every { jwtManager.getKeyIdOrNull("expired-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "expired-token") } returns null

        val result = tokenManager.introspectToken(client, "expired-token", type = null)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null for a token the server refuses`() = runTest {
        val client = mockk<Client>()
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "encoded-token") } returns decodedJwt
        coEvery {
            tokenManager.getAuthenticationToken(decodedJwt)
        } throws oauth2ExceptionOf(INVALID_GRANT, "grant.revoked")

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null when client does not match`() = runTest {
        val client = client("other-client")
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val grant = grant("test-client")
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null for a token of another audience than the client's`() = runTest {
        val client = client("test-client")
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val grant = grant("test-client", "another-audience")
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertNull(result)
    }

    @Test
    fun `decodeAndVerifyAccessOrRefreshTokenOrNull - Returns null for a token naming another key`() = runTest {
        every { jwtManager.getKeyIdOrNull("id-token") } returns PUBLIC_KEY

        val result = tokenManager.decodeAndVerifyAccessOrRefreshTokenOrNull("id-token")

        assertNull(result)
        coVerify(exactly = 0) { jwtManager.decodeAndVerifyOrNull(any(), any()) }
    }

    /** A client of the [AUDIENCE]. */
    private fun client(id: String) = Client(
        id = id,
        secret = null,
        audience = Audience(id = AUDIENCE, tokenAudience = AUDIENCE),
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
    )

    /** A grant opened for the client [clientId], for the audience [audienceId] where a test reaches it. */
    private fun grant(clientId: String, audienceId: String? = null): Grant = mockk {
        every { this@mockk.clientId } returns clientId
        audienceId?.let { every { this@mockk.audienceId } returns it }
    }

    private companion object {
        const val AUDIENCE = "default"
    }
}
