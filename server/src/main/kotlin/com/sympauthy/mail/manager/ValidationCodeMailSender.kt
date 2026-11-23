package com.sympauthy.mail.manager

import com.sympauthy.business.manager.validationcode.ValidationCodeMediaSender
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.mail.model.Mail
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
    @Inject private val uncheckedFeaturesConfig: FeaturesConfig
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

        mailQueue.send(
            mail = Mail.CLAIM_VALIDATION,
            locale = Locale.US,
            receiver = email,
            parameters = mapOf("code" to validationCode.code),
            maxAge = Duration.between(LocalDateTime.now(), validationCode.expirationDate)
        )
    }
}
