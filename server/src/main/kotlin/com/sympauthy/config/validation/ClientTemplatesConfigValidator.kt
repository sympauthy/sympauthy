package com.sympauthy.config.validation

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.parsing.ParsedClientTemplate
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.TEMPLATES_CLIENTS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClientTemplatesConfigValidator(
    @Inject private val fieldValidator: ClientConfigFieldValidator
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClientTemplate>,
        scopesById: Map<String, EnabledScope>,
        flowsById: Map<String, AuthorizationFlow>
    ): Map<String, ClientTemplate> {
        val templates = parsed.mapNotNull { template ->
            validateTemplate(ctx, template, scopesById, flowsById)
        }
        return templates.associateBy { it.id }
    }

    private fun validateTemplate(
        ctx: ConfigParsingContext,
        parsed: ParsedClientTemplate,
        scopesById: Map<String, EnabledScope>,
        flowsById: Map<String, AuthorizationFlow>
    ): ClientTemplate? {
        val configKeyPrefix = "$TEMPLATES_CLIENTS_KEY.${parsed.id}"
        val subCtx = ctx.child()

        val allowedGrantTypes = fieldValidator.validateGrantTypes(
            subCtx, "$configKeyPrefix.allowed-grant-types", parsed.allowedGrantTypes
        )
        val authorizationFlow = fieldValidator.validateAuthorizationFlow(
            subCtx, "$configKeyPrefix.authorization-flow", parsed.authorizationFlowId, flowsById
        )
        val allowedScopes = fieldValidator.validateScopes(
            subCtx, "$configKeyPrefix.allowed-scopes", parsed.allowedScopes, scopesById
        )?.toSet()
        val defaultScopes = fieldValidator.validateScopes(
            subCtx, "$configKeyPrefix.default-scopes", parsed.defaultScopes, scopesById
        )
        val authorizationWebhook = fieldValidator.validateWebhook(parsed.authorizationWebhook)

        ctx.merge(subCtx)
        if (subCtx.hasErrors) return null

        return ClientTemplate(
            id = parsed.id,
            audienceId = parsed.audienceId,
            public = parsed.isPublic,
            allowedGrantTypes = allowedGrantTypes,
            authorizationFlow = authorizationFlow,
            allowedRedirectUris = parsed.allowedRedirectUris,
            allowedScopes = allowedScopes,
            defaultScopes = defaultScopes,
            authorizationWebhook = authorizationWebhook
        )
    }
}
