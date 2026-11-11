package com.sympauthy.business.manager.jwt

import com.sympauthy.business.manager.key.CryptoKeysManager
import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.EnabledAuthConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class JwtManagerTest {

    @MockK
    lateinit var keyManager: CryptoKeysManager

    @MockK
    lateinit var advancedConfig: EnabledAdvancedConfig

    @MockK
    lateinit var authConfig: EnabledAuthConfig

    @InjectMockKs
    lateinit var manager: JwtManager

    @Test
    fun `create - Load the key before stating the claims`() = runTest {
        val keyAlgorithm = JwtAlgorithm.ES256.keyAlgorithm
        every { advancedConfig.publicJwtAlgorithm } returns JwtAlgorithm.ES256
        every { authConfig.issuer } returns "https://auth.example.com"
        coEvery {
            keyManager.getKey(JwtManager.PUBLIC_KEY, keyAlgorithm)
        } returns keyAlgorithm.impl.generate(JwtManager.PUBLIC_KEY)

        manager.create(JwtManager.PUBLIC_KEY) {
            coVerify { keyManager.getKey(JwtManager.PUBLIC_KEY, keyAlgorithm) }
        }
    }
}
