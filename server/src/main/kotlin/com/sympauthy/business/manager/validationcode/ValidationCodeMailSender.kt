package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.manager.mail.MailQueue
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.UIConfig
import com.sympauthy.config.model.orThrow
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
    @Inject private val uncheckedUIConfig: UIConfig
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
        val uiConfig = uncheckedUIConfig.orThrow()

        val email = collectedClaim.value?.toString()
        if (collectedClaim.claim.id != media.claim || email.isNullOrBlank()) {
            throw IllegalArgumentException("${this::class.simpleName} requires a ${media.claim} claim as parameter.")
        }

        val maxAge = Duration.between(LocalDateTime.now(), validationCode.expirationDate)

        mailQueue.send(
            template = "mails/validation_code",
            locale = Locale.US,
            receiver = email,
            subjectKey = "mail.validation_code.subject",
            parameters = mapOf(
                "code" to validationCode.code,
                "displayName" to uiConfig.displayName
            ),
            maxAge = maxAge
        )
    }
}
