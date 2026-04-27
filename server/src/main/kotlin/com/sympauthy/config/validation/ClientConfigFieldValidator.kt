package com.sympauthy.config.validation

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedAuthorizationWebhook
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Shared validation methods for client configuration fields.
 *
 * Used by both [ClientsConfigValidator] and [ClientTemplatesConfigValidator]
 * for cross-domain references and business rule validation.
 */
@Singleton
class ClientConfigFieldValidator(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val authorizationFlowManager: AuthorizationFlowManager
) {

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
     * Look up an authorization flow by ID.
     */
    fun validateAuthorizationFlow(
        ctx: ConfigParsingContext,
        key: String,
        flowId: String?
    ): AuthorizationFlow? {
        if (flowId == null) return null
        val flow = authorizationFlowManager.findByIdOrNull(flowId)
        if (flow == null) {
            ctx.addError(
                configExceptionOf(
                    key, "config.client.authorization_flow.invalid",
                    "flow" to flowId
                )
            )
        }
        return flow
    }

    /**
     * Look up scopes by name via [ScopeManager].
     */
    suspend fun validateScopes(
        ctx: ConfigParsingContext,
        key: String,
        scopes: List<String>?
    ): List<Scope>? {
        if (scopes == null) return null

        val verifiedScopes = scopes.mapIndexedNotNull { index, scope ->
            try {
                val verifiedScope = scopeManager.find(scope)
                if (verifiedScope == null) {
                    ctx.addError(
                        configExceptionOf(
                            "$key[$index]", "config.client.scope.invalid",
                            "scope" to scope
                        )
                    )
                }
                verifiedScope
            } catch (_: Throwable) {
                // Most likely caused by another configuration error.
                null
            }
        }

        return verifiedScopes
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
