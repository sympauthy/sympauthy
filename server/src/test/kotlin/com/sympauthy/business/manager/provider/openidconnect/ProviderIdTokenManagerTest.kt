package com.sympauthy.business.manager.provider.openidconnect

import com.nimbusds.jose.JWSAlgorithm
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.RemoteCallMonitor
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.util.*

class ProviderIdTokenManagerTest {

    private val conditionMonitor = ConditionMonitor()

    private val manager = ProviderIdTokenManager(RemoteCallMonitor(conditionMonitor, OpenTelemetry.noop()))

    @Test
    fun `validateAndExtractClaims - Reports a key set that cannot be fetched to the provider's condition`() =
        runTest {
            // Nothing listens on port 1, and the key set is fetched before the signature is checked.
            val config = ProviderOpenIdConnectConfig(
                clientId = "client",
                clientSecret = "secret",
                scopes = emptyList(),
                issuer = URI("https://issuer.example.com"),
                authorizationUri = URI("https://issuer.example.com/authorize"),
                tokenUri = URI("https://issuer.example.com/token"),
                jwksUri = URI("http://127.0.0.1:1/jwks")
            )
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val idToken = listOf("""{"alg":"RS256","kid":"key"}""", """{"sub":"someone"}""", "signature")
                .joinToString(".") { encoder.encodeToString(it.toByteArray()) }

            assertThrows<BusinessException> {
                manager.validateAndExtractClaims("example", config, idToken, accessToken, null)
            }

            assertTrue(conditionMonitor.isDegraded(Condition.Provider("example")))
        }

    /** OpenID Connect Core 1.0, appendix A.3. */
    private val accessToken = "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"
    private val atHash = "77QmUPtjPfzWtF2AnpK9RQ"

    @Test
    fun `validateAtHash - Accept the hash of the access token from the same response`() {
        assertDoesNotThrow {
            manager.validateAtHash(atHash, accessToken, JWSAlgorithm.RS256, "provider")
        }
    }

    @Test
    fun `validateAtHash - Refuse the hash of another access token`() {
        val exception = assertThrows<BusinessException> {
            manager.validateAtHash(atHash, "another-access-token", JWSAlgorithm.RS256, "provider")
        }

        assertEquals("provider.openid_connect.invalid_at_hash", exception.detailsId)
    }

    @Test
    fun `validateAtHash - Leave the claim unchecked under an algorithm naming no digest`() {
        assertDoesNotThrow {
            manager.validateAtHash(atHash, "another-access-token", JWSAlgorithm.parse("EdDSA"), "provider")
        }
    }
}
