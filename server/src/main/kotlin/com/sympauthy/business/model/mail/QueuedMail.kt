package com.sympauthy.business.model.mail

import java.util.*

/**
 * A mail that has been persisted to the database and is waiting to be sent.
 */
data class QueuedMail(
    /**
     * Unique identifier of the queued mail in the database.
     */
    val id: UUID,
    /**
     * Path to the mail template to render (e.g. "mails/validation_code").
     */
    val template: String,
    /**
     * Locale used for template rendering and subject localization.
     */
    val locale: Locale,
    /**
     * Email address of the recipient.
     */
    val receiver: String,
    /**
     * Message key used to resolve the localized subject line.
     */
    val subjectKey: String,
    /**
     * Key-value pairs injected into the template model.
     */
    val parameters: Map<String, String>
)