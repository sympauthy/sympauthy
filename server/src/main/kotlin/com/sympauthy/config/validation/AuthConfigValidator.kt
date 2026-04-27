package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ParsedAuthConfig
import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.ByPasswordConfigurationProperties.Companion.BY_PASSWORD_KEY
import jakarta.inject.Singleton

@Singleton
class AuthConfigValidator {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedAuthConfig,
        uncheckedClaimsConfig: ClaimsConfig
    ): EnabledAuthConfig? {
        // Validate identifier claims exist and are enabled.
        val enabledClaimsConfig = uncheckedClaimsConfig as? EnabledClaimsConfig
        if (enabledClaimsConfig != null) {
            val enabledClaimIds = enabledClaimsConfig.claims
                .filter { it.enabled }
                .map { it.id }
                .toSet()
            parsed.identifierClaims.forEach { identifierClaimId ->
                if (identifierClaimId !in enabledClaimIds) {
                    ctx.addError(
                        configExceptionOf(
                            "$AUTH_KEY.identifier-claims",
                            "config.auth.identifier_claim.disabled",
                            "claim" to identifierClaimId
                        )
                    )
                }
            }
        }

        // By-password auth requires identifier claims.
        if (parsed.byPasswordEnabled == true && parsed.identifierClaims.isEmpty()) {
            ctx.addError(
                configExceptionOf(
                    "$BY_PASSWORD_KEY.enabled",
                    "config.auth.by_password.no_identifier_claim"
                )
            )
        }

        if (ctx.hasErrors) return null
        return EnabledAuthConfig(
            issuer = parsed.issuer!!,
            token = TokenConfig(
                accessExpiration = parsed.accessExpiration!!,
                idExpiration = parsed.idExpiration!!,
                refreshEnabled = parsed.refreshEnabled!!,
                refreshExpiration = parsed.refreshExpiration,
                dpopRequired = parsed.dpopRequired ?: false
            ),
            authorizationCode = AuthorizationCodeConfig(
                expiration = parsed.authorizationCodeExpiration!!
            ),
            identifierClaims = parsed.identifierClaims,
            userMergingEnabled = parsed.userMergingEnabled!!,
            byPassword = ByPasswordConfig(
                enabled = parsed.byPasswordEnabled!!
            )
        )
    }
}
