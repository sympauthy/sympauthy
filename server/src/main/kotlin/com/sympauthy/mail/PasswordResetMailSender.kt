package com.sympauthy.mail

import com.sympauthy.business.manager.password.PasswordResetMessageSender
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Sends the mails of a password reset.
 *
 * A notice stays worth sending for as long as the code it stands in for would have, so a person reading their inbox
 * once the server restarts is not told about a request they made long before. The notice that the password changed
 * never expires: it is the one way a person learns of a reset they did not make.
 */
@Singleton
class PasswordResetMailSender(
    @Inject private val mailQueue: MailQueue,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig
) : PasswordResetMessageSender {

    override val enabled: Boolean
        get() = mailQueue.enabled

    override suspend fun sendCode(address: String, validationCode: ValidationCode) {
        send(
            mail = Mail.PASSWORD_RESET_CODE,
            address = address,
            parameters = mapOf("code" to validationCode.code),
            maxAge = Duration.between(LocalDateTime.now(), validationCode.expirationDate)
        )
    }

    override suspend fun sendNoAccount(address: String) {
        send(Mail.PASSWORD_RESET_NO_ACCOUNT, address, emptyMap(), noticeMaxAge)
    }

    override suspend fun sendNoPassword(address: String, providerNames: List<String>) {
        send(
            mail = Mail.PASSWORD_RESET_NO_PASSWORD,
            address = address,
            parameters = mapOf("providers" to providerNames.joinToString(PROVIDER_SEPARATOR)),
            maxAge = noticeMaxAge
        )
    }

    override suspend fun sendPasswordChanged(address: String, changedAt: LocalDateTime) {
        send(
            mail = Mail.PASSWORD_CHANGED,
            address = address,
            parameters = mapOf("changedAt" to CHANGED_AT_FORMAT.format(changedAt)),
            maxAge = null
        )
    }

    private val noticeMaxAge: Duration
        get() = uncheckedAdvancedConfig.orThrow().validationCode.expiration

    private suspend fun send(mail: Mail, address: String, parameters: Map<String, String>, maxAge: Duration?) {
        mailQueue.send(
            mail = mail,
            locale = LOCALE,
            receiver = address,
            parameters = parameters,
            maxAge = maxAge
        )
    }

    private companion object {
        /**
         * The language every mail is sent in, until a mail is sent in the language of the person reading it.
         */
        val LOCALE: Locale = Locale.US

        /**
         * What the names of several providers are joined with, in [LOCALE].
         */
        const val PROVIDER_SEPARATOR = " or "

        /**
         * When the password changed, in [LOCALE] and in UTC, the zone this server runs in.
         */
        val CHANGED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm 'UTC'", LOCALE)
    }
}
