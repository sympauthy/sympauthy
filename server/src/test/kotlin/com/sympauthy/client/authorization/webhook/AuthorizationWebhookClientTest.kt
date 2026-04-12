package com.sympauthy.client.authorization.webhook

import com.sympauthy.config.model.AdvancedConfig
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.micronaut.http.client.HttpClient
import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class AuthorizationWebhookClientTest {

    @MockK
    lateinit var httpClient: HttpClient

    @MockK
    lateinit var objectMapper: ObjectMapper

    @MockK
    lateinit var advancedConfig: AdvancedConfig

    @InjectMockKs
    lateinit var client: AuthorizationWebhookClient

    @Test
    fun `computeHmacSha256 - produces correct signature for known input`() {
        // Known test vector: HMAC-SHA256("secret", "message")
        val signature = client.computeHmacSha256("secret", "message")

        assertEquals(
            "8b5f48702995c1598c573db1e21866a9b825d4a794d169d7060a03605796360b",
            signature
        )
    }

    @Test
    fun `computeHmacSha256 - different secrets produce different signatures`() {
        val sig1 = client.computeHmacSha256("secret1", "body")
        val sig2 = client.computeHmacSha256("secret2", "body")

        assert(sig1 != sig2) { "Different secrets should produce different signatures" }
    }

    @Test
    fun `computeHmacSha256 - different bodies produce different signatures`() {
        val sig1 = client.computeHmacSha256("secret", "body1")
        val sig2 = client.computeHmacSha256("secret", "body2")

        assert(sig1 != sig2) { "Different bodies should produce different signatures" }
    }
}
