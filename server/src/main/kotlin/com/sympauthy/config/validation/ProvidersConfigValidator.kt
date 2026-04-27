package com.sympauthy.config.validation

import com.sympauthy.business.model.provider.ProviderUserInfoPathKey.EMAIL
import com.sympauthy.business.model.provider.ProviderUserInfoPathKey.SUB
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ParsedProviderConfig
import com.sympauthy.config.properties.ProviderConfigurationProperties.Companion.PROVIDERS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ProvidersConfigValidator(
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedProviderConfig>
    ): List<ProviderConfig> {
        return parsed.mapNotNull { provider ->
            validateProvider(ctx, provider)
        }
    }

    private fun validateProvider(
        ctx: ConfigParsingContext,
        parsed: ParsedProviderConfig
    ): ProviderConfig? {
        val subCtx = ctx.child()
        val keyPrefix = "$PROVIDERS_KEY.${parsed.id}"

        // Validate auth section present.
        if (!parsed.hasOidc && !parsed.hasOAuth2) {
            subCtx.addError(configExceptionOf(keyPrefix, "config.auth.missing"))
            ctx.merge(subCtx)
            return null
        }

        // Build auth config.
        val auth: ProviderAuthInputConfig? = if (parsed.hasOidc) {
            if (parsed.oidcClientId != null && parsed.oidcClientSecret != null && parsed.oidcIssuer != null) {
                ProviderOpenIdConnectInputConfig(
                    clientId = parsed.oidcClientId,
                    clientSecret = parsed.oidcClientSecret,
                    scopes = parsed.oidcScopes,
                    issuer = parsed.oidcIssuer
                )
            } else null
        } else {
            if (parsed.oauth2ClientId != null && parsed.oauth2ClientSecret != null &&
                parsed.oauth2AuthorizationUri != null && parsed.oauth2TokenUri != null
            ) {
                ProviderOAuth2InputConfig(
                    clientId = parsed.oauth2ClientId,
                    clientSecret = parsed.oauth2ClientSecret,
                    scopes = parsed.oauth2Scopes,
                    authorizationUri = parsed.oauth2AuthorizationUri,
                    tokenUri = parsed.oauth2TokenUri
                )
            } else null
        }

        // Validate user info for OAuth2 providers.
        val userInfo = if (auth is ProviderOAuth2InputConfig) {
            if (!parsed.hasUserInfo) {
                subCtx.addError(configExceptionOf("$keyPrefix.user-info", "config.provider.user_info.missing"))
                null
            } else if (parsed.userInfoUri != null && parsed.userInfoPaths != null) {
                // Validate required path keys.
                if (parsed.userInfoPaths[SUB] == null) {
                    subCtx.addError(
                        configExceptionOf("$keyPrefix.user-info.paths", "config.provider.user_info.missing_subject_key")
                    )
                }
                val authConfig = uncheckedAuthConfig as? EnabledAuthConfig
                if (authConfig?.userMergingEnabled == true && parsed.userInfoPaths[EMAIL] == null) {
                    subCtx.addError(
                        configExceptionOf("$keyPrefix.user-info.paths", "config.provider.user_info.missing_email_key")
                    )
                }
                if (subCtx.hasErrors) null
                else com.sympauthy.business.model.provider.config.ProviderUserInfoConfig(
                    uri = parsed.userInfoUri,
                    paths = parsed.userInfoPaths
                )
            } else null
        } else null

        ctx.merge(subCtx)
        if (subCtx.hasErrors || parsed.name == null || auth == null) return null

        return ProviderConfig(
            id = parsed.id,
            name = parsed.name,
            auth = auth,
            userInfo = userInfo
        )
    }
}
