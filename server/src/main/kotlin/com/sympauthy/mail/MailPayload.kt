package com.sympauthy.mail

import io.micronaut.serde.annotation.Serdeable

/**
 * What a queued mail holds beside its kind, as the queue item's payload: the [receiver] it goes to, the [locale] it
 * is drawn in, as a language tag, and the [parameters] it was sent for.
 *
 * It holds nothing read when the mail is attempted, which is drawn then: the brand, the name it is signed under, the
 * minutes a code has left.
 */
@Serdeable
data class MailPayload(
    val receiver: String,
    val locale: String,
    val parameters: Map<String, String>
)
