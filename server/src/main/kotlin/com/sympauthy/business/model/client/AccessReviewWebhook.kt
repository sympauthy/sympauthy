package com.sympauthy.business.model.client

import java.net.URI

/**
 * Configuration for handing a client the security contexts a person has been seen in, and letting it
 * decide whether the request it is validating goes through.
 *
 * This server scores nothing: it records where somebody signed in from and asks. Everything a risk
 * engine would want is in the payload, and the client already holds the rest of the context about
 * what the person was doing.
 */
data class AccessReviewWebhook(
    /**
     * The URL of the external server's webhook endpoint.
     */
    val url: URI,
    /**
     * The HMAC-SHA256 signing key used to sign the request body.
     * The signature is sent in the `X-SympAuthy-Signature` header.
     */
    val secret: String,
    /**
     * What makes the webhook worth calling.
     */
    val on: AccessReviewTrigger,
    /**
     * The behavior to adopt when the webhook call fails (network error, timeout, non-2xx response,
     * or an answer naming no decision).
     */
    val onFailure: AccessReviewOnFailure,
)

enum class AccessReviewTrigger {
    /**
     * Call it where the context the request comes from carries no `allow` of its own.
     *
     * The trigger is a property of the decision rather than of the sighting, and deliberately so. Were
     * it "a context never seen before", the observation the first attempt writes would make the second
     * attempt familiar: an attacker would be asked about once, denied, and let through on every retry
     * from the same address. A denial is not recorded as a decision, so it never disarms this.
     */
    NEW_CONTEXT,

    /**
     * Call it on every validation. UserInfo can be called on every request a client serves, so this
     * puts another server's availability on this server's hot path.
     */
    EVERY_VALIDATION
}

enum class AccessReviewOnFailure {
    /**
     * Refuse the request the webhook could not answer for. Fail-closed, which is the right default for
     * a security control and the wrong one for availability: a client whose webhook is down cannot
     * read UserInfo.
     */
    DENY,

    /**
     * Let the request through. The client keeps its availability and loses the control while its
     * webhook is unreachable.
     */
    ALLOW
}
