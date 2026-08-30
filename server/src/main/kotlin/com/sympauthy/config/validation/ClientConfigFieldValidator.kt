package com.sympauthy.config.validation

import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedAuthorizationWebhook
import jakarta.inject.Singleton

/**
 * Shared validation methods for client configuration fields.
 *
 * Used by both [ClientsConfigValidator] and [ClientTemplatesConfigValidator]
 * for cross-domain references and business rule validation.
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
        scopesById: Map<String, Scope>,
        audienceId: String? = null
    ): List<Scope>? {
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
