package com.sympauthy.config.validation

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

    suspend fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClientTemplate>
    ): Map<String, ClientTemplate> {
        val templates = parsed.mapNotNull { template ->
            validateTemplate(ctx, template)
        }
        return templates.associateBy { it.id }
    }

    private suspend fun validateTemplate(
        ctx: ConfigParsingContext,
        parsed: ParsedClientTemplate
    ): ClientTemplate? {
        val configKeyPrefix = "$TEMPLATES_CLIENTS_KEY.${parsed.id}"
        val subCtx = ctx.child()

        val allowedGrantTypes = fieldValidator.validateGrantTypes(
            subCtx, "$configKeyPrefix.allowed-grant-types", parsed.allowedGrantTypes
        )
        val authorizationFlow = fieldValidator.validateAuthorizationFlow(
            subCtx, "$configKeyPrefix.authorization-flow", parsed.authorizationFlowId
        )
        val allowedScopes = fieldValidator.validateScopes(
            subCtx, "$configKeyPrefix.allowed-scopes", parsed.allowedScopes
        )?.toSet()
        val defaultScopes = fieldValidator.validateScopes(
            subCtx, "$configKeyPrefix.default-scopes", parsed.defaultScopes
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
