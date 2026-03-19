package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.BuiltInGrantableScopeId
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.data.repository.AuthenticationTokenRepository
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class IdTokenGeneratorTest {

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var tokenMapper: EncodedAuthenticationTokenMapper

    @MockK
    lateinit var uncheckedAuthConfig: AuthConfig

    @InjectMockKs
    lateinit var generator: IdTokenGenerator

    @Test
    fun shouldGenerateIdToken() {
        assertTrue(generator.shouldGenerateIdToken(listOf(BuiltInGrantableScopeId.OPENID)))
        assertFalse(generator.shouldGenerateIdToken(emptyList()))
    }
}
