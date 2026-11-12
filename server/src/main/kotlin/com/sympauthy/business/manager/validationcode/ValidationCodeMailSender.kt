package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.code.ValidationCodeReason.RESET_PASSWORD
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.mail.MailBrandResolver
import com.sympauthy.mail.MailQueue
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * Components in charge of sending the validation code to the user by email.
 */
@Singleton
class ValidationCodeMailSender(
    @Inject private val mailQueue: MailQueue,
    @Inject private val uncheckedFeaturesConfig: FeaturesConfig,
    @Inject private val mailBrandResolver: MailBrandResolver
) : ValidationCodeMediaSender {

    override val media = EMAIL

    override val enabled: Boolean
        get() {
            return mailQueue.enabled && uncheckedFeaturesConfig.orThrow().emailValidation
        }

    override suspend fun sendValidationCode(
        user: User,
        collectedClaim: CollectedClaim,
        validationCode: ValidationCode
    ) {
        val email = collectedClaim.value?.toString()
        require(collectedClaim.claim.id == media.claim && !email.isNullOrBlank()) {
            "${this::class.simpleName} requires a ${media.claim} claim as parameter."
        }

        val maxAge = Duration.between(LocalDateTime.now(), validationCode.expirationDate)
        // One code serves every reason of its media: a code that also resets the password says so.
        val mail = if (RESET_PASSWORD in validationCode.reasons) RESET_PASSWORD_MAIL else VALIDATION_CODE_MAIL

        mailQueue.send(
            template = "mails/$mail",
            locale = Locale.US,
            receiver = email,
            subjectKey = "mail.$mail.subject",
            parameters = mapOf(
                "code" to validationCode.code,
                "displayName" to mailBrandResolver.displayName,
                "expiryMinutes" to maxAge.toMinutesRoundedUp().toString()
            ),
            maxAge = maxAge
        )
    }

    /**
     * The minutes a reader has left, rounded up so that a code valid for 9 minutes 59 does not read as 9.
     */
    private fun Duration.toMinutesRoundedUp(): Long = (toSeconds() + 59).floorDiv(60).coerceAtLeast(1)

    private companion object {
        const val VALIDATION_CODE_MAIL = "validation_code"
        const val RESET_PASSWORD_MAIL = "reset_password"
    }
}
