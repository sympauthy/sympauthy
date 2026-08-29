package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.DisabledAuthorizationFlowsConfig
import com.sympauthy.config.model.EnabledAuthorizationFlowsConfig
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getOrNull
import com.sympauthy.config.parsing.AuthorizationFlowsConfigParser
import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties
import com.sympauthy.config.validation.AuthorizationFlowsConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AuthorizationFlowsConfigFactory(
    @Inject private val flowsParser: AuthorizationFlowsConfigParser,
    @Inject private val flowsValidator: AuthorizationFlowsConfigValidator,
    @Inject private val uncheckedUrlsConfig: UrlsConfig,
    @Inject private val uncheckedMfaConfig: MfaConfig
) {

    @Singleton
    fun provideAuthorizationFlows(
        propertiesList: List<AuthorizationFlowConfigurationProperties>
    ): AuthorizationFlowsConfig {
        val rootUri = uncheckedUrlsConfig.getOrNull()?.root
            ?: return DisabledAuthorizationFlowsConfig(emptyList())

        val ctx = ConfigParsingContext()
        val parsed = flowsParser.parse(ctx, propertiesList)
        val totpMfaEnabled = (uncheckedMfaConfig as? EnabledMfaConfig)?.totp == true
        val flows = flowsValidator.validate(ctx, parsed, rootUri, totpMfaEnabled)
        return if (ctx.hasErrors) DisabledAuthorizationFlowsConfig(ctx.errors)
        else EnabledAuthorizationFlowsConfig(flowsValidator.bundledFlow(rootUri), flows)
    }
}
