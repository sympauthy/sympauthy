package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.actas.ActAsRuleManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.ACCESS_DENIED
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TARGET
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.EnabledAudiencesConfig
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TokenExchangeManagerTest {

    // Clear the global MockK registry after each test so inline mocks with per-test-only stubs do not leak into
    // other test classes that assert on unnecessary stubbings (e.g. TokenManagerTest).
    @AfterEach
    fun tearDown() = clearAllMocks()

    private val jwtManager = mockk<JwtManager>()
    private val tokenManager = mockk<TokenManager>()
    private val accessTokenGenerator = mockk<AccessTokenGenerator>()
    private val userManager = mockk<UserManager>()
    private val collectedClaimManager = mockk<CollectedClaimManager>()
    private val actAsRuleManager = mockk<ActAsRuleManager>()

    private val defaultAudience = Audience(id = "default", tokenAudience = "default-aud")
    private val backendAudience = Audience(id = "backend", tokenAudience = "backend-aud")

    private val actingClient = mockk<Client> {
        every { id } returns CLIENT_ID
        every { audience } returns mockk {
            every { id } returns "default"
            every { tokenAudience } returns "default-aud"
        }
    }

    private val userId = UUID.randomUUID()
    private val accessTokenType = TokenExchangeManager.ACCESS_TOKEN_TYPE

    private fun manager(audiences: List<Audience> = listOf(defaultAudience, backendAudience)) =
        TokenExchangeManager(
            jwtManager = jwtManager,
            tokenManager = tokenManager,
            accessTokenGenerator = accessTokenGenerator,
            userManager = userManager,
            collectedClaimManager = collectedClaimManager,
            actAsRuleManager = actAsRuleManager,
            uncheckedAudiencesConfig = EnabledAudiencesConfig(audiences)
        )

    private fun mockActorToken(clientId: String = CLIENT_ID, userId: UUID? = null): AuthenticationToken {
        val token = mockk<AuthenticationToken>()
        every { token.clientId } returns clientId
        every { token.userId } returns userId
        every { token.id } returns UUID.randomUUID()
        return token
    }

    private fun stubValidSubjectToken(token: AuthenticationToken) {
        val decoded = mockk<DecodedJwt>()
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, SUBJECT_TOKEN) } returns decoded
        coEvery { tokenManager.getAuthenticationToken(decoded) } returns token
    }

    private fun stubKnownUser() {
        val user = mockk<User> { every { id } returns userId }
        coEvery { userManager.findByIdOrNull(userId) } returns user
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
    }

    @Test
    fun `unsupported subject_token_type is rejected`() = runTest {
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, "unexpected", userId.toString(), null)
        }
        assertEquals(INVALID_REQUEST, ex.errorCode)
    }

    @Test
    fun `subject_token issued to another client is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken(clientId = "another-client"))
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, accessTokenType, userId.toString(), null)
        }
        assertEquals(INVALID_GRANT, ex.errorCode)
    }

    @Test
    fun `subject_token bound to a user is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken(userId = UUID.randomUUID()))
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, accessTokenType, userId.toString(), null)
        }
        assertEquals(INVALID_GRANT, ex.errorCode)
    }

    @Test
    fun `malformed requested_subject is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, accessTokenType, "not-a-uuid", null)
        }
        assertEquals(INVALID_TARGET, ex.errorCode)
    }

    @Test
    fun `unknown target user is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        coEvery { userManager.findByIdOrNull(userId) } returns null
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, accessTokenType, userId.toString(), null)
        }
        assertEquals(INVALID_TARGET, ex.errorCode)
    }

    @Test
    fun `denied by act-as rules is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        stubKnownUser()
        coEvery { actAsRuleManager.isActAsAllowed(actingClient, emptyList()) } returns false
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, accessTokenType, userId.toString(), null)
        }
        assertEquals(ACCESS_DENIED, ex.errorCode)
    }

    @Test
    fun `unknown requested audience is rejected`() = runTest {
        stubValidSubjectToken(mockActorToken())
        stubKnownUser()
        coEvery { actAsRuleManager.isActAsAllowed(actingClient, emptyList()) } returns true
        val ex = assertFailsWith<OAuth2Exception> {
            manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, accessTokenType, userId.toString(), "unknown")
        }
        assertEquals(INVALID_TARGET, ex.errorCode)
    }

    @Test
    fun `success without requested audience defaults to the client audience`() = runTest {
        val actorToken = mockActorToken()
        stubValidSubjectToken(actorToken)
        stubKnownUser()
        coEvery { actAsRuleManager.isActAsAllowed(actingClient, emptyList()) } returns true
        val encoded = mockk<EncodedAuthenticationToken>()
        coEvery { accessTokenGenerator.generateActAsAccessToken(any(), any(), any(), any()) } returns encoded

        val result = manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, accessTokenType, userId.toString(), null)

        assertEquals(encoded, result)
        coVerify {
            accessTokenGenerator.generateActAsAccessToken(
                userId = userId,
                actorToken = actorToken,
                tokenAudience = "default-aud",
                dpopJkt = null
            )
        }
    }

    @Test
    fun `success with requested audience by id uses the configured token audience`() = runTest {
        val actorToken = mockActorToken()
        stubValidSubjectToken(actorToken)
        stubKnownUser()
        coEvery { actAsRuleManager.isActAsAllowed(actingClient, emptyList()) } returns true
        val encoded = mockk<EncodedAuthenticationToken>()
        coEvery { accessTokenGenerator.generateActAsAccessToken(any(), any(), any(), any()) } returns encoded

        manager().exchangeForActAsToken(actingClient, SUBJECT_TOKEN, accessTokenType, userId.toString(), "backend")

        coVerify {
            accessTokenGenerator.generateActAsAccessToken(
                userId = userId,
                actorToken = actorToken,
                tokenAudience = "backend-aud",
                dpopJkt = null
            )
        }
    }

    companion object {
        private const val CLIENT_ID = "discord-bot"
        private const val SUBJECT_TOKEN = "subject-token"
    }
}
