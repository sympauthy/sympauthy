package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.manager.actas.ActAsRuleManager
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TARGET
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.monitoring.telemetry.TokenMetrics
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@ExtendWith(MockKExtension::class)
class TokenExchangeManagerTest {

    private val jwtManager = mockk<JwtManager>()
    private val tokenManager = mockk<TokenManager>()
    /**
     * The real manager behind a spy: whether the subject token's grant was opened for the acting client is a
     * function of the two alone, so every scenario runs it, and only opening the act-as grant is stubbed.
     */
    private val grantManager = spyk(
        GrantManager(mockk(), mockk(), mockk(), mockk(), mockk(), TokenMetrics(OpenTelemetry.noop()))
    )
    private val accessTokenGenerator = mockk<AccessTokenGenerator>()
    private val claimAclManager = mockk<ClaimAclManager>()
    private val userManager = mockk<UserManager>()
    private val collectedClaimManager = mockk<CollectedClaimManager>()
    private val actAsRuleManager = mockk<ActAsRuleManager>()

    private val defaultAudience = Audience(id = "default", tokenAudience = "default-aud")
    private val backendAudience = Audience(id = "backend", tokenAudience = "backend-aud")

    private val actingClient = Client(
        id = CLIENT_ID,
        secret = null,
        audience = defaultAudience,
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
    )

    private val userId = UUID.randomUUID()

    private fun manager() =
        TokenExchangeManager(
            jwtManager = jwtManager,
            tokenManager = tokenManager,
            grantManager = grantManager,
            accessTokenGenerator = accessTokenGenerator,
            claimAclManager = claimAclManager,
            userManager = userManager,
            collectedClaimManager = collectedClaimManager,
            actAsRuleManager = actAsRuleManager
        )

    /**
     * A client-credentials token issued to the acting client for its audience: its grant names no user and no
     * other grant.
     */
    private fun mockActorToken(): AuthenticationToken = mockSubjectToken(mockk {
        every { clientId } returns CLIENT_ID
        every { audienceId } returns defaultAudience.id
        every { actorGrantId } returns null
        every { userId } returns null
    })

    private fun mockSubjectToken(grant: Grant): AuthenticationToken = mockk {
        every { this@mockk.grant } returns grant
    }

    /**
     * Stubs opening the act-as grant for [audience], and issuing [encoded] under it through the doors of a bearer
     * token issued to the acting client.
     */
    private fun stubIssuance(
        actorToken: AuthenticationToken,
        audience: Audience,
        encoded: EncodedAuthenticationToken = mockk()
    ): Grant {
        val grant = mockk<Grant> {
            every { grantType } returns GrantType.TOKEN_EXCHANGE
            every { consentedScopes } returns emptyList()
            every { grantedScopes } returns emptyList()
            every { clientScopes } returns emptyList()
            every { authenticationDate } returns null
        }
        val doors = mockk<ClaimAclTokenDoors>()
        coEvery { grantManager.createTokenExchangeGrant(userId, audience.id, actorToken) } returns grant
        every {
            claimAclManager.getTokenDoors(
                ClaimAclContext(actingClient, emptyList(), emptyList(), authenticationDate = null, bound = false),
                any()
            )
        } returns doors
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.TOKEN_EXCHANGE, null)
        } returns encoded
        return grant
    }

    /**
     * Stubs [token] as the subject token the acting client presents.
     */
    private fun stubValidSubjectToken(token: AuthenticationToken) {
        val decoded = mockk<DecodedJwt>()
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, SUBJECT_TOKEN) } returns decoded
        coEvery { tokenManager.getAuthenticationToken(decoded) } returns token
    }

    private fun stubKnownUser() {
        val user = mockk<User> { every { id } returns userId }
        coEvery { userManager.findByIdOrNull(userId) } returns user
    }

    /**
     * The target user's claims as the audience the token is being issued for has them. Naming that audience is
     * the assertion: a manager reading the acting client's instead, or reading them unnarrowed, reaches no stub.
     */
    private fun stubTargetAudienceClaims(audienceId: String) {
        coEvery { collectedClaimManager.findByUserIdAndAudience(userId, audienceId) } returns emptyList()
    }

    @Test
    fun `subject_token that does not verify is rejected`() = runTest {
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, SUBJECT_TOKEN) } throws InvalidJwtException("jwt.expired")
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))
        }
        assertEquals(INVALID_GRANT, ex.errorCode)
        assertEquals("jwt.expired", ex.detailsId)
    }

    @Test
    fun `a signing key that will not load is not reported as a bad subject_token`() = runTest {
        val keyFailure = BusinessException(
            recoverable = false,
            detailsId = "jwt.invalid_key",
            values = mapOf("name" to ACCESS_KEY),
            recommendedStatus = INTERNAL_SERVER_ERROR
        )
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, SUBJECT_TOKEN) } throws keyFailure

        val ex = assertFailsWith<BusinessException> {
            manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))
        }

        assertSame(keyFailure, ex)
        assertEquals(mapOf("name" to ACCESS_KEY), ex.values)
    }

    @Test
    fun `subject_token issued to another client is rejected`() = runTest {
        // The owner is checked first, so nothing else about the token is read.
        stubValidSubjectToken(mockSubjectToken(mockk { every { clientId } returns "another-client" }))
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))
        }
        assertEquals(INVALID_GRANT, ex.errorCode)
        assertEquals("token_exchange.subject_token_not_owned", ex.detailsId)
    }

    @Test
    fun `subject_token issued for another audience than the acting client belongs to now is rejected`() = runTest {
        stubValidSubjectToken(mockSubjectToken(mockk {
            every { clientId } returns CLIENT_ID
            every { audienceId } returns backendAudience.id
        }))
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))
        }
        assertEquals(INVALID_GRANT, ex.errorCode)
        assertEquals("grant.mismatching_audience", ex.detailsId)
    }

    @Test
    fun `subject_token bound to a user is rejected`() = runTest {
        stubValidSubjectToken(mockSubjectToken(mockk {
            every { clientId } returns CLIENT_ID
            every { audienceId } returns defaultAudience.id
            every { actorGrantId } returns null
            every { userId } returns UUID.randomUUID()
        }))
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))
        }
        assertEquals(INVALID_GRANT, ex.errorCode)
    }

    @Test
    fun `subject_token that already has an actor is rejected as unsupported chaining`() = runTest {
        // An act-as token carries an actor and a user; presenting it would chain act-as tokens, which is unsupported.
        stubValidSubjectToken(mockSubjectToken(mockk {
            every { clientId } returns CLIENT_ID
            every { audienceId } returns defaultAudience.id
            // An actor is checked before the user binding, so the user is never read.
            every { actorGrantId } returns UUID.randomUUID()
        }))
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))
        }
        assertEquals(INVALID_GRANT, ex.errorCode)
        assertEquals("token_exchange.subject_token_has_actor", ex.detailsId)
    }

    @Test
    fun `malformed requested_subject is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, exchange("not-a-uuid"))
        }
        assertEquals(INVALID_TARGET, ex.errorCode)
    }

    @Test
    fun `unknown target user is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        coEvery { userManager.findByIdOrNull(userId) } returns null
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))
        }
        assertEquals(INVALID_TARGET, ex.errorCode)
    }

    @Test
    fun `denied by act-as rules is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        stubKnownUser()
        stubTargetAudienceClaims("default")
        coEvery { actAsRuleManager.isActAsAllowed(actingClient, emptyList()) } returns false
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))
        }
        assertEquals(INVALID_REQUEST, ex.errorCode)
    }

    @Test
    fun `requested audience other than the acting client's is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        stubKnownUser()
        // The target user's claims are left unstubbed: reaching the assertion is proof the audience was refused
        // before the act-as rules read anything about the user.
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(
                actingClient, exchange(userId.toString(), audience = backendAudience.tokenAudience)
            )
        }
        assertEquals(INVALID_TARGET, ex.errorCode)
        assertEquals("token_exchange.invalid_target", ex.detailsId)
        assertEquals(backendAudience.tokenAudience, ex.values["target"])
    }

    @Test
    fun `unknown requested audience is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        stubKnownUser()
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(
                actingClient, exchange(userId.toString(), audience = "unknown")
            )
        }
        assertEquals(INVALID_TARGET, ex.errorCode)
    }

    @Test
    fun `success without requested audience issues for the acting client's audience`() = runTest {
        val actorToken = mockActorToken()
        stubTargetAudienceClaims(defaultAudience.id)
        stubValidSubjectToken(actorToken)
        stubKnownUser()
        coEvery { actAsRuleManager.isActAsAllowed(actingClient, emptyList()) } returns true
        val encoded = mockk<EncodedAuthenticationToken>()
        stubIssuance(actorToken, defaultAudience, encoded)

        val result = manager().exchangeForActAsToken(actingClient, exchange(userId.toString()))

        assertEquals(encoded, result)
    }

    @Test
    fun `success with the acting client's audience by id`() = runTest {
        assertIssuedForClientAudience(requestedAudience = defaultAudience.id)
    }

    @Test
    fun `success with the acting client's audience by token audience`() = runTest {
        assertIssuedForClientAudience(requestedAudience = defaultAudience.tokenAudience)
    }

    private suspend fun assertIssuedForClientAudience(requestedAudience: String) {
        val actorToken = mockActorToken()
        stubValidSubjectToken(actorToken)
        stubKnownUser()
        stubTargetAudienceClaims(defaultAudience.id)
        coEvery { actAsRuleManager.isActAsAllowed(actingClient, emptyList()) } returns true
        val encoded = mockk<EncodedAuthenticationToken>()
        stubIssuance(actorToken, defaultAudience, encoded)

        val result = manager().exchangeForActAsToken(
            actingClient, exchange(userId.toString(), audience = requestedAudience)
        )

        assertSame(encoded, result)
    }

    private fun exchange(requestedSubject: String, audience: String? = null) = TokenRequest.TokenExchange(
        subjectToken = SUBJECT_TOKEN,
        requestedSubject = requestedSubject,
        audience = audience
    )

    companion object {
        private const val CLIENT_ID = "discord-bot"
        private const val SUBJECT_TOKEN = "subject-token"
    }
}
