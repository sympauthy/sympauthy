package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.manager.mail.MailSender
import com.sympauthy.business.manager.mail.TemplatedMailBuilderFactory
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.UIConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.context.annotation.Requires
import io.micronaut.email.javamail.sender.JavaMailConfiguration
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Components in charge of sending the validation code to the user by email.
 */
@Singleton
@Requires(beans = [JavaMailConfiguration::class])
class ValidationCodeMailSender(
    @Inject private val mailBuilderFactory: TemplatedMailBuilderFactory,
    @Inject private val mailSender: MailSender,
    @Inject private val uncheckedFeaturesConfig: FeaturesConfig,
    @Inject private val uncheckedUIConfig: UIConfig
) : ValidationCodeMediaSender {

    override val media = EMAIL

    override val enabled: Boolean
        get() {
            return uncheckedFeaturesConfig.orThrow().emailValidation
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

        val builder = mailBuilderFactory
            .builder(
                template = "mails/validation_code",
                locale = Locale.US
            ).apply {
                receiver(email)

                set("code", validationCode.code)
                set("displayName", uiConfig.displayName)

                localizedSubject("mail.validation_code.subject")
            }

        mailSender.send(builder.builder)
    }
}
