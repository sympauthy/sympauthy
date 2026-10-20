package com.sympauthy.config.validation

import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ParsedAuthConfig
import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.AuthorizationCodeConfigurationProperties.Companion.AUTHORIZATION_CODE_KEY
import com.sympauthy.config.properties.ByPasswordConfigurationProperties.Companion.BY_PASSWORD_KEY
import com.sympauthy.config.properties.GrantConfigurationProperties.Companion.GRANT_KEY
import com.sympauthy.config.properties.InteractiveFlowConfigurationProperties.Companion.INTERACTIVE_FLOW_KEY
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

        validateLifetime(ctx, "$TOKEN_KEY.access-expiration", parsed.accessExpiration)
        validateLifetime(ctx, "$TOKEN_KEY.id-expiration", parsed.idExpiration)
        validateLifetime(ctx, "$TOKEN_KEY.refresh-expiration", parsed.refreshExpiration)
        validateLifetime(ctx, "$AUTHORIZATION_CODE_KEY.expiration", parsed.authorizationCodeExpiration)
        validateLifetime(ctx, "$INTERACTIVE_FLOW_KEY.expiration", parsed.interactiveFlowExpiration)
        validateLifetime(ctx, "$GRANT_KEY.expiration", parsed.grantExpiration)

        if (ctx.hasErrors) return null
        return EnabledAuthConfig(
            issuer = parsed.issuer!!,
            token = TokenConfig(
                accessExpiration = parsed.accessExpiration!!,
                // An id token lives as long as the access token it is issued beside, unless a deployment says
                // otherwise.
                idExpiration = parsed.idExpiration ?: parsed.accessExpiration!!,
                refreshEnabled = parsed.refreshEnabled!!,
                refreshExpiration = parsed.refreshExpiration,
                dpopRequired = parsed.dpopRequired ?: false
            ),
            authorizationCode = AuthorizationCodeConfig(
                expiration = parsed.authorizationCodeExpiration!!
            ),
            interactiveFlow = InteractiveFlowConfig(
                expiration = parsed.interactiveFlowExpiration!!
            ),
            grant = GrantConfig(
                expiration = parsed.grantExpiration
            ),
            identifierClaims = parsed.identifierClaims,
            userMergingEnabled = parsed.userMergingEnabled!!,
            byPassword = ByPasswordConfig(
                enabled = parsed.byPasswordEnabled!!
            )
        )
    }
}
