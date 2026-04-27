package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedMfaConfig
import com.sympauthy.config.properties.MfaTotpConfigurationProperties.Companion.MFA_TOTP_KEY
import jakarta.inject.Singleton

@Singleton
class MfaConfigValidator {
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedMfaConfig
    ) {
        if (parsed.required == true && parsed.totpEnabled == false) {
            ctx.addError(
                configExceptionOf("$MFA_TOTP_KEY.enabled", "config.mfa.totp.disabled_when_required")
            )
        }
    }
}
