package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedFeaturesConfig
import com.sympauthy.config.properties.FeaturesConfigurationProperties.Companion.FEATURES_KEY
import jakarta.inject.Singleton

@Singleton
class FeaturesConfigValidator {

    /**
     * [mailConfigured] is whether the deployment configured a way to send mail, without which the
     * features that send one cannot be turned on.
     */
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedFeaturesConfig,
        mailConfigured: Boolean
    ) {
        if (parsed.emailValidation == true && !mailConfigured) {
            ctx.addError(
                configExceptionOf(
                    "$FEATURES_KEY.email-validation",
                    "config.features.email_validation.no_sender"
                )
            )
        }
    }
}
