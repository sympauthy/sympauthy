package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jwt.JWTClaimsSet
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.BuiltInGrantableScopeId
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.TokenConfig
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.*

@ExtendWith(MockKExtension::class)
class IdTokenGeneratorTest {

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var generatedClaimsManager: GeneratedClaimsManager

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var tokenMapper: EncodedAuthenticationTokenMapper

    @MockK
    lateinit var uncheckedAdvancedConfig: EnabledAdvancedConfig

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    @InjectMockKs
    lateinit var generator: IdTokenGenerator

    private val savedEntity = slot<AuthenticationTokenEntity>()

    @Test
    fun `generateIdToken - Claim the hash of the access token it was issued beside`() = runTest {
        val userId = UUID.randomUUID()
        val accessToken = mockk<EncodedAuthenticationToken> {
            every { token } returns ACCESS_TOKEN
        }
        val claimsSet = issue(userId, accessToken)

        assertEquals("77QmUPtjPfzWtF2AnpK9RQ", claimsSet.getStringClaim("at_hash"))
    }

    @Test
    fun `generateIdToken - Carry the grant of the refresh token it is issued from`() = runTest {
        val sessionId = UUID.randomUUID()
        val refreshToken = mockRefreshToken(sessionId = sessionId)

        issue(refreshToken)

        val entity = savedEntity.captured
        assertEquals(refreshToken.userId, entity.userId)
        assertEquals("client", entity.clientId)
        assertEquals(listOf(BuiltInGrantableScopeId.OPENID), entity.grantedScopes.toList())
        assertEquals(listOf(CONSENTED_SCOPE), entity.consentedScopes.toList())
        assertEquals(sessionId, entity.sessionId)
        assertEquals("refresh_token", entity.grantType)
    }

    @Test
    fun `generateIdToken - Claim no nonce on a refresh`() = runTest {
        val claimsSet = issue(mockRefreshToken(sessionId = UUID.randomUUID()))

        assertNull(claimsSet.getClaim("nonce"))
    }

    @Test
    fun `generateIdToken - Issue one where the authorization granted openid`() = runTest {
        val userId = UUID.randomUUID()
        stubGeneration(userId)

        val idToken = generator.generateIdToken(
            oauth2 = oauth2(grantedScopes = listOf(BuiltInGrantableScopeId.OPENID)),
            userId = userId,
            accessToken = mockk { every { token } returns ACCESS_TOKEN }
        )

        assertNotNull(idToken)
    }

    @Test
    fun `generateIdToken - Issue none where the authorization did not grant openid`() = runTest {
        val idToken = generator.generateIdToken(
            oauth2 = oauth2(grantedScopes = listOf(CONSENTED_SCOPE)),
            userId = UUID.randomUUID(),
            accessToken = mockk()
        )

        assertNull(idToken)
    }

    @Test
    fun `generateIdToken - Issue none where the grant a refresh descends from did not carry openid`() = runTest {
        val refreshToken = mockRefreshToken(
            sessionId = UUID.randomUUID(),
            grantedScopes = listOf(CONSENTED_SCOPE)
        )

        assertNull(generator.generateIdToken(refreshToken, mockk()))
    }

    private fun oauth2(grantedScopes: List<String>) = InteractiveFlowSessionOAuth2(
        sessionId = UUID.randomUUID(),
        clientId = "client",
        redirectUri = "https://client.example.com/callback",
        requestedScopes = grantedScopes,
        grantedScopes = grantedScopes
    )

    private fun mockRefreshToken(
        sessionId: UUID,
        grantedScopes: List<String> = listOf(BuiltInGrantableScopeId.OPENID)
    ): AuthenticationToken {
        val id = UUID.randomUUID()
        val scopes = grantedScopes
        return mockk {
            every { userId } returns id
            every { clientId } returns "client"
            every { this@mockk.grantedScopes } returns scopes
            every { consentedScopes } returns listOf(CONSENTED_SCOPE)
            every { this@mockk.sessionId } returns sessionId
        }
    }

    private suspend fun issue(refreshToken: AuthenticationToken): JWTClaimsSet {
        val accessToken = mockk<EncodedAuthenticationToken> {
            every { token } returns ACCESS_TOKEN
        }
        val builder = stubGeneration(
            userId = checkNotNull(refreshToken.userId),
            consentedScopes = refreshToken.consentedScopes
        )

        generator.generateIdToken(refreshToken, accessToken)

        return builder.build()
    }

    private fun stubGeneration(
        userId: UUID,
        consentedScopes: List<String> = emptyList()
    ): JWTClaimsSet.Builder {
        every { uncheckedAdvancedConfig.publicJwtAlgorithm } returns JwtAlgorithm.RS256
        every { uncheckedAuthConfig.token } returns mockk<TokenConfig> {
            every { idExpiration } returns Duration.ofMinutes(5)
        }
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(userId, consentedScopes)
        } returns emptyList()
        coEvery { tokenRepository.save(capture(savedEntity)) } answers { firstArg<AuthenticationTokenEntity>() }
        every { generatedClaimsManager.computeSubject(userId) } returns userId.toString()
        every { tokenMapper.toEncodedAuthenticationToken(any(), any()) } returns mockk()

        val builder = JWTClaimsSet.Builder()
        coEvery { jwtManager.create(JwtManager.PUBLIC_KEY, any(), any()) } answers {
            arg<JWTClaimsSet.Builder.() -> Unit>(2)(builder)
            "encoded-id-token"
        }
        return builder
    }

    private suspend fun issue(userId: UUID, accessToken: EncodedAuthenticationToken): JWTClaimsSet {
        val builder = stubGeneration(userId)

        generator.generateIdToken(
            userId = userId,
            clientId = "client",
            grantedScopes = listOf(BuiltInGrantableScopeId.OPENID),
            consentedScopes = emptyList(),
            sessionId = null,
            accessToken = accessToken,
            grantType = "authorization_code"
        )

        return builder.build()
    }

    private companion object {
        const val CONSENTED_SCOPE = "email"
        const val ACCESS_TOKEN = "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"
    }
}
