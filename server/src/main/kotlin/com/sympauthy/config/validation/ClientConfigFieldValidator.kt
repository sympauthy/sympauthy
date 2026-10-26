package com.sympauthy.config.validation

import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedAuthorizationWebhook
import jakarta.inject.Singleton

/**
 * Validation methods for the fields a client and the template it takes its defaults from both declare:
 * the cross-domain references and the business rules over each of them.
 */
@Singleton
class ClientConfigFieldValidator {

    /**
     * Validate that REFRESH_TOKEN requires AUTHORIZATION_CODE.
     */
    fun validateGrantTypes(
        ctx: ConfigParsingContext,
        configKey: String,
        grantTypes: Set<GrantType>?
    ): Set<GrantType>? {
        if (grantTypes == null) return null
        if (GrantType.REFRESH_TOKEN in grantTypes && GrantType.AUTHORIZATION_CODE !in grantTypes) {
            ctx.addError(
                configExceptionOf(
                    configKey, "config.client.allowed_grant_types.refresh_token_requires_authorization_code"
                )
            )
            return null
        }
        return grantTypes
    }

    /**
     * Look up an authorization flow by ID among the [flowsById] the server serves.
     */
    fun validateAuthorizationFlow(
        ctx: ConfigParsingContext,
        key: String,
        flowId: String?,
        flowsById: Map<String, AuthorizationFlow>
    ): AuthorizationFlow? {
        if (flowId == null) return null
        val flow = flowsById[flowId]
        if (flow == null) {
            ctx.addError(
                configExceptionOf(
                    key, "config.client.authorization_flow.invalid",
                    "flow" to flowId,
                    "flows" to flowsById.keys.joinToString(", ")
                )
            )
        }
        return flow
    }

    /**
     * Look up scopes by name among the [scopesById] the server serves.
     *
     * When [audienceId] is provided, also validates that each scope belongs to the given audience
     * (or has no audience restriction).
     */
    fun validateScopes(
        ctx: ConfigParsingContext,
        key: String,
        scopes: List<String>?,
        scopesById: Map<String, EnabledScope>,
        audienceId: String? = null
    ): List<EnabledScope>? {
        if (scopes == null) return null

        return scopes.mapIndexedNotNull { index, scope ->
            val verifiedScope = scopesById[scope]
            if (verifiedScope == null) {
                ctx.addError(
                    configExceptionOf(
                        "$key[$index]", "config.client.scope.invalid",
                        "scope" to scope
                    )
                )
                return@mapIndexedNotNull null
            }
            if (audienceId != null
                && verifiedScope.audienceId != null
                && verifiedScope.audienceId != audienceId
            ) {
                ctx.addError(
                    configExceptionOf(
                        "$key[$index]", "config.client.scope.audience_mismatch",
                        "scope" to scope,
                        "scopeAudience" to verifiedScope.audienceId,
                        "clientAudience" to audienceId
                    )
                )
                return@mapIndexedNotNull null
            }
            verifiedScope
        }
    }

    /**
     * Look up default scopes the way [validateScopes] does, and also refuse a [ClientScope] among
     * them under `config.client.default_scopes.not_user_scope`, against the index it was written at.
     *
     * The default scopes are what an authorization request naming no scope is answered with, and a
     * client scope is obtained in a `client_credentials` flow only: one named here could never be
     * granted, and each such request would be refused at the authorization endpoint instead of once,
     * at startup.
     */
    fun validateDefaultScopes(
        ctx: ConfigParsingContext,
        key: String,
        scopes: List<String>?,
        scopesById: Map<String, EnabledScope>,
        audienceId: String? = null
    ): List<EnabledScope>? {
        scopes?.forEachIndexed { index, scope ->
            if (scopesById[scope] is ClientScope) {
                ctx.addError(
                    configExceptionOf(
                        "$key[$index]", "config.client.default_scopes.not_user_scope",
                        "scope" to scope
                    )
                )
            }
        }
        return validateScopes(ctx, key, scopes, scopesById, audienceId)
    }

    /**
     * Build the final [AuthorizationWebhook] from parsed webhook data.
     */
    fun validateWebhook(
        parsed: ParsedAuthorizationWebhook?
    ): AuthorizationWebhook? {
        if (parsed == null) return null
        return AuthorizationWebhook(
            url = parsed.url!!,
            secret = parsed.secret!!,
            onFailure = parsed.onFailure!!
        )
    }
}
