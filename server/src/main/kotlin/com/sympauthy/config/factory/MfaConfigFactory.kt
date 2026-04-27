package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.DisabledMfaConfig
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.parsing.MfaConfigParser
import com.sympauthy.config.properties.MfaConfigurationProperties
import com.sympauthy.config.properties.MfaTotpConfigurationProperties
import com.sympauthy.config.validation.MfaConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class MfaConfigFactory(
    @Inject private val mfaParser: MfaConfigParser,
    @Inject private val mfaValidator: MfaConfigValidator
) {

    @Singleton
    fun provideMfaConfig(
        properties: MfaConfigurationProperties,
        totpProperties: MfaTotpConfigurationProperties
    ): MfaConfig {
        val ctx = ConfigParsingContext()
        val parsed = mfaParser.parse(ctx, properties, totpProperties)
        mfaValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledMfaConfig(ctx.errors)
        else EnabledMfaConfig(required = parsed.required!!, totp = parsed.totpEnabled!!)
    }
}
