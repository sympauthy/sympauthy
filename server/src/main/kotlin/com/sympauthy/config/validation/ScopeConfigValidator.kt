package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.isAdminScope
import com.sympauthy.business.model.oauth2.isBuiltInClientScope
import com.sympauthy.business.model.oauth2.isBuiltInGrantableScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.CustomScopeConfig
import com.sympauthy.config.model.OpenIdConnectScopeConfig
import com.sympauthy.config.model.ScopeConfig
import com.sympauthy.config.parsing.ParsedScopeConfig
import com.sympauthy.config.properties.ScopeConfigurationProperties.Companion.SCOPES_KEY
import jakarta.inject.Singleton

@Singleton
class ScopeConfigValidator {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedScopeConfig>,
        audiencesById: Map<String, Audience>
    ): List<ScopeConfig> {
        return parsed.mapNotNull { scope ->
            validateScope(ctx, scope, audiencesById)
        }
    }

    private fun validateScope(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeConfig,
        audiencesById: Map<String, Audience>
    ): ScopeConfig? {
        val configKeyPrefix = "$SCOPES_KEY.${parsed.id}"

        // Built-in scopes are not configurable.
        if (parsed.id.isAdminScope()) {
            ctx.addError(
                configExceptionOf(configKeyPrefix, "config.scope.admin_not_configurable", "scope" to parsed.id)
            )
            return null
        }
        if (parsed.id.isBuiltInGrantableScope()) {
            ctx.addError(
                configExceptionOf(configKeyPrefix, "config.scope.builtin_not_configurable", "scope" to parsed.id)
            )
            return null
        }
        if (parsed.id.isBuiltInClientScope()) {
            ctx.addError(
                configExceptionOf(configKeyPrefix, "config.scope.builtin_not_configurable", "scope" to parsed.id)
            )
            return null
        }

        // Validate audience cross-reference.
        val audienceId = validateAudienceId(
            ctx, parsed.audienceId, audiencesById,
            "$configKeyPrefix.audience", "config.scope.audience.not_found"
        )

        return if (parsed.isOpenIdConnect) {
            OpenIdConnectScopeConfig(
                scope = parsed.id,
                enabled = parsed.enabled ?: true,
                audienceId = audienceId
            )
        } else {
            validateCustomScope(ctx, parsed, audienceId)
        }
    }

    private fun validateCustomScope(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeConfig,
        audienceId: String?
    ): ScopeConfig? {
        val configKeyPrefix = "$SCOPES_KEY.${parsed.id}"
        val consentable = when (parsed.type) {
            null, "grantable" -> false
            "consentable" -> true
            "client" -> {
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.type",
                        "config.scope.custom_client_type_not_allowed",
                        "scope" to parsed.id
                    )
                )
                return null
            }
            else -> {
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.type",
                        "config.scope.invalid_type",
                        "scope" to parsed.id,
                        "type" to parsed.type
                    )
                )
                return null
            }
        }
        return CustomScopeConfig(
            scope = parsed.id,
            consentable = consentable,
            audienceId = audienceId
        )
    }
}
