package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jwt.JWTClaimsSet
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.jwt.JwtAlgorithm
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun shouldGenerateIdToken() {
        assertTrue(generator.shouldGenerateIdToken(listOf(BuiltInGrantableScopeId.OPENID)))
        assertFalse(generator.shouldGenerateIdToken(emptyList()))
    }

    @Test
    fun `generateIdToken - Claim the hash of the access token it was issued beside`() = runTest {
        val userId = UUID.randomUUID()
        val accessToken = mockk<EncodedAuthenticationToken> {
            every { token } returns "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"
        }
        val claimsSet = issue(userId, accessToken)

        assertEquals("77QmUPtjPfzWtF2AnpK9RQ", claimsSet.getStringClaim("at_hash"))
    }

    private suspend fun issue(userId: UUID, accessToken: EncodedAuthenticationToken): JWTClaimsSet {
        every { uncheckedAdvancedConfig.publicJwtAlgorithm } returns JwtAlgorithm.RS256
        every { uncheckedAuthConfig.token } returns mockk<TokenConfig> {
            every { idExpiration } returns Duration.ofMinutes(5)
        }
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(userId, emptyList())
        } returns emptyList()
        coEvery { tokenRepository.save(any()) } answers { firstArg<AuthenticationTokenEntity>() }
        every { generatedClaimsManager.computeSubject(userId) } returns userId.toString()
        every { tokenMapper.toEncodedAuthenticationToken(any(), any()) } returns mockk()

        val builder = JWTClaimsSet.Builder()
        coEvery { jwtManager.create(JwtManager.PUBLIC_KEY, any(), any()) } answers {
            arg<JWTClaimsSet.Builder.() -> Unit>(2)(builder)
            "encoded-id-token"
        }

        generator.generateIdToken(
            userId = userId,
            clientId = "client",
            grantedScopes = emptyList(),
            consentedScopes = emptyList(),
            sessionId = null,
            accessToken = accessToken,
            grantType = "authorization_code"
        )

        return builder.build()
    }
}
