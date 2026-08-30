package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedFeaturesConfig
import com.sympauthy.config.properties.FeaturesConfigurationProperties.Companion.FEATURES_KEY
import io.micronaut.email.EmailSender
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class FeaturesConfigValidator(
    /**
     * Present when the deployment configured a way to send mail. It is the sender itself rather than
     * the mail section of the configuration, because that section can be switched on and still leave
     * nothing able to send.
     */
    @Inject private val emailSender: EmailSender<Any, Any>? = null
) {
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedFeaturesConfig
    ) {
        if (parsed.emailValidation == true && emailSender == null) {
            ctx.addError(
                configExceptionOf(
                    "$FEATURES_KEY.email-validation",
                    "config.features.email_validation.no_sender"
                )
            )
        }
    }
}
