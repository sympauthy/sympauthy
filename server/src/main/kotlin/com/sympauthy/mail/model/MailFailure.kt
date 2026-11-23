package com.sympauthy.mail.model

import com.sympauthy.client.remoteErrorType
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * What an attempt at a mail that failed says, which decides whether the mail is tried again and how the attempt is
 * reported. The split is `docs/design/mail.md`'s.
 */
internal sealed interface MailFailure {

    /**
     * The mail server is unwell: it could not be reached, did not answer in time, closed the connection before it
     * answered, refused this server's credentials, or refused this server's sending — its session, its sender. The
     * mail is retried, and the call degrades the mail sender's condition, typed by [errorType].
     */
    data class MailServerUnwell(val errorType: String) : MailFailure

    /**
     * The mail server answered a recipient or the mail with a temporary refusal, [replyCode]. The mail is retried,
     * and the mail server, having answered, is not degraded by it.
     */
    data class TemporaryRefusal(val replyCode: Int) : MailFailure

    /**
     * The mail server refused a recipient or the mail for good, or reported the mailbox full, under [replyCode]. The
     * mail is given up, since every later attempt would fail the same way.
     */
    data class PermanentRefusal(val replyCode: Int) : MailFailure

    /**
     * Nothing above: a template that failed to draw, or a failure carrying no reply and no network cause. The mail
     * is given up, and the failure is the server's own to report.
     */
    data object Unclassified : MailFailure

    companion object {

        /**
         * What [failure], thrown sending a mail or composing it, says.
         *
         * The network's own failures are named rather than any [java.io.IOException]: a template that cannot be
         * read fails with one too, inside the same send, and is this one mail's. A refusal is told by the reply it
         * carries, not by how JavaMail reports it.
         */
        fun of(failure: Throwable): MailFailure {
            val chain = generateSequence(failure) { it.cause }.toList()
            if (chain.any { it is AuthenticationFailedException }) return MailServerUnwell(AUTHENTICATION)
            if (chain.any { it.isNetworkFailure() }) return MailServerUnwell(failure.remoteErrorType())
            return chain.firstNotNullOfOrNull(SmtpReply::ofOrNull)?.failure() ?: Unclassified
        }

        private fun Throwable.isNetworkFailure(): Boolean =
            this is SocketException || this is SocketTimeoutException || this is UnknownHostException ||
                this is SSLException

        /**
         * What a failure the mail server refused this server's credentials for is typed by.
         */
        private const val AUTHENTICATION = "authentication"
    }
}

/**
 * A reply a mail server refused part of a send with: its [code], the [enhancedCode] RFC 3463 defines where it sent
 * one, such as `5.1.1`, and the [stage] of the conversation it answered.
 */
internal class SmtpReply(
    val code: Int,
    val enhancedCode: String?,
    val stage: Stage
) {

    /**
     * Which part of the conversation a reply answered.
     */
    enum class Stage {
        /** The mail server's greeting, before this server said anything. */
        GREETING,

        /** This server's own sending: its session, its credentials, its sender. */
        SESSION,

        /** One recipient of the mail. */
        RECIPIENT,

        /** The mail itself, once its recipients were accepted. */
        MESSAGE
    }

    /**
     * What this reply makes of the attempt it ended.
     *
     * No reply at all, the connection closed before the mail server answered, is the mail server being unwell at
     * whichever stage it came. A refusal of the session refuses this server's sending whatever its code. A greeting
     * that asks to be tried later is a temporary refusal, and any other refuses the session. A recipient or the mail
     * is refused as its reply code says, a security or policy status, `x.7.x`, included: a recipient's domain
     * rejects that way for a policy of its own, and taking it for this server's sending would retry that one mail
     * for good. A full mailbox, `4.2.2` or `5.2.2`, is given up whatever its reply code.
     */
    fun failure(): MailFailure = when {
        code == NO_REPLY -> MailFailure.MailServerUnwell(CONNECTION_CLOSED)
        stage == Stage.SESSION -> MailFailure.MailServerUnwell(code.toString())
        stage == Stage.GREETING ->
            if (code in TEMPORARY) MailFailure.TemporaryRefusal(code) else MailFailure.MailServerUnwell(code.toString())

        enhancedCode in MAILBOX_FULL -> MailFailure.PermanentRefusal(code)
        code in TEMPORARY -> MailFailure.TemporaryRefusal(code)
        code in PERMANENT -> MailFailure.PermanentRefusal(code)
        else -> MailFailure.Unclassified
    }

    companion object {

        /**
         * The reply [failure] carries, or null where it carries none of its own.
         *
         * JavaMail raises a typed exception for what a send command was answered with, and a bare
         * [MessagingException] holding the reply as its message for the rest of the session — its greeting
         * included, which it words apart.
         */
        fun ofOrNull(failure: Throwable): SmtpReply? = when (failure) {
            is SMTPAddressFailedException -> parse(failure.returnCode, failure.message, Stage.RECIPIENT)
            is SMTPSenderFailedException -> parse(failure.returnCode, failure.message, Stage.SESSION)
            is SMTPSendFailedException -> parse(failure.returnCode, failure.message, stageOf(failure.command))
            is MessagingException -> failure.message?.let(::parseUntyped)
            else -> null
        }

        private fun stageOf(command: String?): Stage {
            val verb = command?.trim()?.substringBefore(' ')?.substringBefore(':')?.uppercase()
            return when (verb) {
                "RCPT" -> Stage.RECIPIENT
                "DATA", "BDAT", "." -> Stage.MESSAGE
                else -> Stage.SESSION
            }
        }

        private fun parseUntyped(message: String): SmtpReply? {
            GREETING_MESSAGE.find(message)?.let { match ->
                val code = match.groupValues[1].toIntOrNull() ?: NO_REPLY
                return parse(code, match.groupValues[1] + match.groupValues[2], Stage.GREETING)
            }
            val code = REPLY.find(message)?.groupValues?.get(1)?.toInt() ?: return null
            return parse(code, message, Stage.SESSION)
        }

        private fun parse(code: Int, response: String?, stage: Stage): SmtpReply {
            return SmtpReply(
                code = code,
                enhancedCode = response?.let { ENHANCED.find(it.trim()) }?.groupValues?.get(1),
                stage = stage
            )
        }

        private val TEMPORARY = 400..499
        private val PERMANENT = 500..599

        /** The code JavaMail reports a reply it never got under, the connection having closed first. */
        private const val NO_REPLY = -1

        /** What a mail server closing the connection before it answered is typed by. */
        private const val CONNECTION_CLOSED = "connection_closed"

        /** The statuses a full mailbox is reported under. */
        private val MAILBOX_FULL = setOf("4.2.2", "5.2.2")

        /** A reply line: three digits, then a space or a dash. */
        private val REPLY = Regex("""^(\d{3})(?:[ -]|$)""")

        /** The enhanced status code following a reply's code, `class.subject.detail`. */
        private val ENHANCED = Regex("""^\d{3}[ -]([245]\.\d{1,3}\.\d{1,3})\b""")

        /** How JavaMail words a greeting it was refused with, `[EOF]` where the connection closed first. */
        private val GREETING_MESSAGE =
            Regex("""Got bad greeting from SMTP host: .*?, response: (\d{3}|\[EOF])(.*)""")
    }
}
