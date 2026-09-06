package com.sympauthy.business.manager.securitycontext

import com.sympauthy.business.model.client.AccessReviewOnFailure
import com.sympauthy.business.model.client.AccessReviewTrigger
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.business.model.securitycontext.AccessReviewReason
import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.business.model.securitycontext.SecurityContext
import com.sympauthy.client.accessreview.webhook.AccessReviewWebhookClient
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookContext
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookRequest
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookResult
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Asks a client whether the request it is validating is one it still wants served, given the places
 * the person has been seen in.
 *
 * SympAuthy scores nothing: no impossible travel, no velocity, no reputation. It records the material
 * and asks, and everything a risk engine would want is in what it hands over — the client already
 * holds the rest of the context about what the person was doing.
 *
 * A deployment whose client configured no webhook pays nothing at all here: no row is written, and
 * the request is allowed without anything being asked.
 */
@Singleton
class AccessReviewManager(
    @Inject private val advancedConfig: AdvancedConfig,
    @Inject private val securityContextManager: SecurityContextManager,
    @Inject private val accessReviewWebhookClient: AccessReviewWebhookClient
) {

    /**
     * Review the request [observedRequest] came from against the places [userId] has been seen in, and
     * answer what [client] decided about it.
     *
     * A client with no access-review webhook allows everything, and nothing is recorded for it. Where
     * there is one, the place is recorded first — that is what makes it part of the person's history
     * whatever the answer — and the webhook is called unless the trigger says this place needs no
     * asking: under [AccessReviewTrigger.NEW_CONTEXT] a place already carrying an
     * [AccessReviewDecision.ALLOW] is served without a call, which is what keeps a returning person
     * off another server's availability.
     *
     * A webhook that failed, or answered with something that is not a decision, is
     * [com.sympauthy.business.model.client.AccessReviewWebhook.onFailure] — and records nothing, so
     * one timeout cannot stamp an allow and disarm the trigger for good.
     */
    suspend fun reviewAccess(
        client: Client,
        userId: UUID,
        reason: AccessReviewReason,
        observedRequest: ObservedRequest
    ): AccessReviewDecision {
        val webhook = client.accessReviewWebhook ?: return AccessReviewDecision.ALLOW

        val current = securityContextManager.recordObservation(observedRequest, userId = userId)
        if (webhook.on == AccessReviewTrigger.NEW_CONTEXT && current.lastDecision == AccessReviewDecision.ALLOW) {
            return AccessReviewDecision.ALLOW
        }

        val past = securityContextManager.listPastContexts(
            userId = userId,
            limit = advancedConfig.orThrow().accessReviewWebhook.pastContexts,
            excluding = current.id
        )
        val result = accessReviewWebhookClient.callWebhook(
            accessReviewWebhook = webhook,
            request = AccessReviewWebhookRequest(
                userId = userId.toString(),
                clientId = client.id,
                reason = reason.wireName,
                current = current.toWebhookContext(),
                past = past.map { it.toWebhookContext() }
            )
        )

        return when (result) {
            is AccessReviewWebhookResult.Success -> {
                securityContextManager.markReviewed(current, result.decision)
                result.decision
            }

            is AccessReviewWebhookResult.Failure -> when (webhook.onFailure) {
                AccessReviewOnFailure.DENY -> AccessReviewDecision.DENY
                AccessReviewOnFailure.ALLOW -> AccessReviewDecision.ALLOW
            }
        }
    }

    /**
     * This place as the client is shown it. The fingerprint and the retention are this server's own
     * and are not published; the dates are, as instants, because a local date without its zone is not
     * a contract another system can read.
     */
    private fun SecurityContext.toWebhookContext() = AccessReviewWebhookContext(
        ip = ip,
        userAgent = userAgent,
        country = geo.country,
        region = geo.region,
        city = geo.city,
        firstSeenDate = firstSeenDate.asInstant(),
        lastSeenDate = lastSeenDate.asInstant(),
        observationCount = observationCount,
        new = observationCount == 1
    )

    private fun LocalDateTime.asInstant(): String = toInstant(ZoneOffset.UTC).toString()
}
