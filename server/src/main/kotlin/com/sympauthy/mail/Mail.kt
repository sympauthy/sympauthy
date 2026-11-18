package com.sympauthy.mail

/**
 * A mail this server sends, by the template it is drawn from.
 *
 * Each is counted under its own value on `sympauthy.mail.sends`, the lowercased name, the notices included —
 * see `docs/design/telemetry.md`.
 */
enum class Mail(
    /**
     * Name of the template under `views/mails`, which its messages are keyed by in `mail_messages.properties`.
     */
    val template: String
) {
    /** The code proving the value of a claim. */
    CLAIM_VALIDATION("validation_code"),

    /** The code proving a password reset. */
    PASSWORD_RESET_CODE("reset_password"),

    /** The notice to an address no account uses, that no code was sent. */
    PASSWORD_RESET_NO_ACCOUNT("reset_password_no_account"),

    /** The notice to an account holding no password, that it has none to reset. */
    PASSWORD_RESET_NO_PASSWORD("reset_password_no_password"),

    /** The notice that a reset changed the password. */
    PASSWORD_CHANGED("password_changed");

    /**
     * Path of the template, as the queue records it.
     */
    val templatePath: String
        get() = "mails/$template"

    /**
     * Key of the message the subject is read from.
     */
    val subjectKey: String
        get() = "mail.$template.subject"

    /**
     * Value of the `mail` attribute a send is counted under.
     */
    val metricName: String
        get() = name.lowercase()

    companion object {
        /**
         * The mail drawn from the template at [templatePath], or null where this build draws none from it: a
         * row a previous build queued.
         */
        fun ofTemplatePathOrNull(templatePath: String): Mail? = entries.firstOrNull { it.templatePath == templatePath }
    }
}
