package com.sympauthy.client.authorization.webhook

import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookRequest
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookResponse
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType.APPLICATION_JSON
import io.micronaut.http.client.HttpClient
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withTimeout
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Singleton
class AuthorizationWebhookClient(
    @Inject private val httpClient: HttpClient,
    @Inject private val objectMapper: ObjectMapper,
    @Inject private val advancedConfig: AdvancedConfig
) {

    suspend fun callWebhook(
        authorizationWebhook: AuthorizationWebhook,
        request: AuthorizationWebhookRequest
    ): AuthorizationWebhookResponse {
        val body = objectMapper.writeValueAsString(request)
        val signature = computeHmacSha256(authorizationWebhook.secret, body)
        val timeout = advancedConfig.orThrow().authorizationWebhook.timeout

        val httpRequest = HttpRequest
            .POST(authorizationWebhook.url, body)
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)
            .header(SIGNATURE_HEADER, "$SIGNATURE_PREFIX$signature")

        return withTimeout(timeout) {
            httpClient.retrieve(httpRequest, AuthorizationWebhookResponse::class.java)
                .awaitFirst()
        }
    }

    internal fun computeHmacSha256(secret: String, body: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        val hash = mac.doFinal(body.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val SIGNATURE_HEADER = "X-SympAuthy-Signature"
        private const val SIGNATURE_PREFIX = "sha256="
    }
}
