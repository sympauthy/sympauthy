package com.sympauthy.platform.monitoring.telemetry.manager

import com.sympauthy.platform.monitoring.model.Outcome
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.inject.Singleton

/**
 * The mails this server hands the mail server.
 *
 * A send is counted beside the call carrying it, which `sympauthy.remote_call.duration` times. See
 * `docs/design/telemetry.md` for which mails are a send.
 */
@Singleton
class MailMetrics(
    openTelemetry: OpenTelemetry
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val sends = meter.counterBuilder(MAIL_SENDS)
        .setDescription("Mails handed to the mail server, which mail each was, and how each send ended.")
        .setUnit("{mail}")
        .build()

    /**
     * Record that the mail [mail] names was handed to the mail server, and that the send ended as [outcome].
     */
    fun recordSend(mail: String, outcome: Outcome) {
        sends.add(1, Attributes.builder().put(MAIL, mail).putOutcome(outcome).build())
    }

    companion object {
        const val MAIL_SENDS = "sympauthy.mail.sends"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.mail"

        private val MAIL = AttributeKey.stringKey("mail")
    }
}
