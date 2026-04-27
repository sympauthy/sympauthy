package com.sympauthy.config.validation

import com.sympauthy.business.manager.mail.MailQueue
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedFeaturesConfig
import com.sympauthy.config.properties.FeaturesConfigurationProperties.Companion.FEATURES_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class FeaturesConfigValidator(
    @Inject private val mailQueue: MailQueue
) {
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedFeaturesConfig
    ) {
        if (parsed.emailValidation == true && !mailQueue.enabled) {
            ctx.addError(
                configExceptionOf(
                    "$FEATURES_KEY.email-validation",
                    "config.features.email_validation.no_sender"
                )
            )
        }
    }
}
