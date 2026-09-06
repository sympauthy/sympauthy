package com.sympauthy.client.accessreview.webhook.model

import com.sympauthy.business.model.securitycontext.AccessReviewDecision

/**
 * Result of calling a client's access-review webhook.
 */
sealed class AccessReviewWebhookResult {

    /**
     * The webhook answered with a decision this server knows how to apply.
     */
    data class Success(
        val decision: AccessReviewDecision
    ) : AccessReviewWebhookResult()

    /**
     * The call failed, or was answered with something that is not a decision.
     *
     * An unrecognised decision is a failure rather than an allow: a webhook answering `"yes"` goes
     * down the `on-failure` path its client configured instead of letting the request through.
     */
    data class Failure(
        val message: String,
        val cause: Exception? = null
    ) : AccessReviewWebhookResult()
}
