package com.sympauthy.config.validation

import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ParsedAuthConfig
import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.ByPasswordConfigurationProperties.Companion.BY_PASSWORD_KEY
import com.sympauthy.config.properties.TokenConfigurationProperties.Companion.TOKEN_KEY
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AuthConfigValidator {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedAuthConfig,
        uncheckedClaimsConfig: ClaimsConfig
    ): EnabledAuthConfig? {
        // Validate identifier claims exist, are enabled, and are of a type an identifier can be compared in.
        val enabledClaimsConfig = uncheckedClaimsConfig as? EnabledClaimsConfig
        if (enabledClaimsConfig != null) {
            val enabledClaims = enabledClaimsConfig.claims
                .filterIsInstance<EnabledClaim>()
                .associateBy { it.id }
            parsed.identifierClaims.forEach { identifierClaimId ->
                val claim = enabledClaims[identifierClaimId]
                if (claim == null) {
                    ctx.addError(
                        configExceptionOf(
                            "$AUTH_KEY.identifier-claims",
                            "config.auth.identifier_claim.disabled",
                            "claim" to identifierClaimId
                        )
                    )
                } else if (!claim.dataType.canIdentify) {
                    // Which types may identify, and why the rest may not, is ClaimDataType.canIdentify.
                    ctx.addError(
                        configExceptionOf(
                            "$AUTH_KEY.identifier-claims",
                            "config.auth.identifier_claim.unidentifiable_type",
                            "claim" to identifierClaimId,
                            "type" to claim.dataType.wireName
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

        // Zero reads as "no lifetime of its own" and means the opposite: every grant would be opened already
        // expired, and nobody could sign in. Leaving the key out is how a grant is given no lifetime.
        parsed.grantExpiration?.let { grantExpiration ->
            if (!grantExpiration.isPositive) {
                ctx.addError(
                    configExceptionOf(
                        "$TOKEN_KEY.grant-expiration",
                        "config.auth.token.grant_expiration.not_positive",
                        "duration" to grantExpiration.toString()
                    )
                )
            }
        }

        if (ctx.hasErrors) return null
        return EnabledAuthConfig(
            issuer = parsed.issuer!!,
            token = TokenConfig(
                accessExpiration = parsed.accessExpiration!!,
                idExpiration = parsed.idExpiration!!,
                refreshEnabled = parsed.refreshEnabled!!,
                refreshExpiration = parsed.refreshExpiration,
                grantExpiration = parsed.grantExpiration,
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
