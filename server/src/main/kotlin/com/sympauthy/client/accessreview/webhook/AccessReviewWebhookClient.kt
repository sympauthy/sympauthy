package com.sympauthy.client.accessreview.webhook

import com.sympauthy.business.model.client.AccessReviewWebhook
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookRequest
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookResponse
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookResult
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.wireName
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType.APPLICATION_JSON
import io.micronaut.http.client.HttpClient
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.time.withTimeout
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HTTP client responsible for calling a client's access-review webhook endpoint.
 *
 * It is the twin of [com.sympauthy.client.authorization.webhook.AuthorizationWebhookClient]: the body
 * as JSON, signed with HMAC-SHA256 in the `X-SympAuthy-Signature` header, under a timeout, and every
 * failure answered as a value rather than thrown.
 *
 * The timeout is `advanced.webhooks.access-review.timeout`, shorter than the authorization webhook's
 * because this one sits on the path a token is validated through: a stalled webhook here holds a
 * connection on a path that has nothing to do with signing in.
 */
@Singleton
class AccessReviewWebhookClient(
    @Inject private val httpClient: HttpClient,
    @Inject private val objectMapper: ObjectMapper,
    @Inject private val advancedConfig: AdvancedConfig
) {

    /**
     * Post the places [request] carries to the URL [accessReviewWebhook] names, signed with the secret
     * it holds beside that URL, and answer the decision it returned.
     *
     * A call that failed, and an answer naming no decision this server knows, are both
     * [AccessReviewWebhookResult.Failure] — the caller applies what the client configured for that
     * rather than letting the request through.
     */
    suspend fun callWebhook(
        accessReviewWebhook: AccessReviewWebhook,
        request: AccessReviewWebhookRequest
    ): AccessReviewWebhookResult {
        val body = objectMapper.writeValueAsString(request)
        val signature = computeHmacSha256(accessReviewWebhook.secret, body)
        val timeout = advancedConfig.orThrow().accessReviewWebhook.timeout

        val httpRequest = HttpRequest
            .POST(accessReviewWebhook.url, body)
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)
            .header(SIGNATURE_HEADER, "$SIGNATURE_PREFIX$signature")

        return try {
            val response = withTimeout(timeout) {
                httpClient.retrieve(httpRequest, AccessReviewWebhookResponse::class.java)
                    .awaitFirst()
            }
            decisionOf(response.decision)
        } catch (e: TimeoutCancellationException) {
            // The webhook ran out of the time it was given, which is a webhook that did not answer. It is
            // caught ahead of the clause below because a timeout is a cancellation as far as Kotlin is
            // concerned, and rethrowing it would turn the timeout this class exists to apply into a
            // failure of the request instead of a decision the client configured for.
            AccessReviewWebhookResult.Failure(message = e.message ?: "The webhook did not answer in time.")
        } catch (e: CancellationException) {
            // The caller is gone rather than the webhook: answering here would apply a decision on behalf
            // of a request nobody is waiting for, manufactured out of a cancellation.
            throw e
        } catch (e: Exception) {
            AccessReviewWebhookResult.Failure(
                message = e.message ?: e::class.simpleName ?: "Unknown error",
                cause = e
            )
        }
    }

    /**
     * The decision [decision] names, compared against the name each is published under, or a failure
     * where it names none of them.
     */
    private fun decisionOf(decision: String): AccessReviewWebhookResult {
        val answered = AccessReviewDecision.entries.firstOrNull { it.wireName == decision }
            ?: return AccessReviewWebhookResult.Failure(
                message = "The webhook answered \"$decision\", which is not a decision."
            )
        return AccessReviewWebhookResult.Success(answered)
    }

    internal fun computeHmacSha256(secret: String, body: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        val hash = mac.doFinal(body.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        internal const val SIGNATURE_HEADER = "X-SympAuthy-Signature"
        internal const val SIGNATURE_PREFIX = "sha256="
    }
}
