package com.sympauthy.mail.model

import java.util.*

/**
 * A [mail] queued for [receiver], drawn in [locale] with the [parameters] it was sent for.
 *
 * It holds nothing read when the mail is attempted, which is drawn then: the brand, the name it is signed under, the
 * minutes a code has left.
 */
data class QueuedMail(
    val mail: Mail,
    val receiver: String,
    val locale: Locale,
    val parameters: Map<String, String>
)
