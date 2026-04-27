package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedClient
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClientsConfigValidator(
    @Inject private val fieldValidator: ClientConfigFieldValidator
) {

    suspend fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClient>,
        audiencesById: Map<String, Audience>
    ): List<Client> {
        return parsed.mapNotNull { client ->
            validateClient(ctx, client, audiencesById)
        }
    }

    private suspend fun validateClient(
        ctx: ConfigParsingContext,
        parsed: ParsedClient,
        audiencesById: Map<String, Audience>
    ): Client? {
        val subCtx = ctx.child()
        val configKeyPrefix = "$CLIENTS_KEY.${parsed.id}"

        // Validate audience cross-reference.
        val audience = validateAndResolveAudience(subCtx, parsed, audiencesById, configKeyPrefix)

        // Validate secret for non-public clients.
        if (!parsed.isPublic && parsed.secret == null) {
            subCtx.addError(configExceptionOf("$configKeyPrefix.secret", "config.missing"))
        }

        // Validate grant types (already resolved with template fallback by parser).
        val allowedGrantTypes = if (parsed.allowedGrantTypes != null) {
            fieldValidator.validateGrantTypes(
                subCtx, "$configKeyPrefix.allowed-grant-types", parsed.allowedGrantTypes
            )
        } else {
            subCtx.addError(
                configExceptionOf(
                    "$configKeyPrefix.allowed-grant-types",
                    "config.client.allowed_grant_types.missing",
                    "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                )
            )
            null
        }

        // Validate authorization flow.
        val authorizationFlow = when {
            parsed.authorizationFlowId != null -> fieldValidator.validateAuthorizationFlow(
                subCtx, "$configKeyPrefix.authorization-flow", parsed.authorizationFlowId
            )
            else -> parsed.template?.authorizationFlow
        }

        // Validate redirect URIs (already resolved with template fallback by parser).
        val allowedRedirectUris = if (allowedGrantTypes?.contains(GrantType.AUTHORIZATION_CODE) != false) {
            if (parsed.allowedRedirectUris != null) {
                parsed.allowedRedirectUris
            } else {
                subCtx.addError(
                    configExceptionOf("$configKeyPrefix.allowed-redirect-uris", "config.client.allowed_redirect_uris.missing")
                )
                null
            }
        } else {
            if (parsed.hasExplicitRedirectUris) {
                subCtx.addError(
                    configExceptionOf("$configKeyPrefix.allowed-redirect-uris", "config.client.allowed_redirect_uris.unnecessary")
                )
            }
            emptyList()
        }

        // Validate scopes.
        val allowedScopes = when {
            parsed.allowedScopes != null -> fieldValidator.validateScopes(
                subCtx, "$configKeyPrefix.allowed-scopes", parsed.allowedScopes
            )?.toSet()
            else -> parsed.template?.allowedScopes
        }
        val defaultScopes = when {
            parsed.defaultScopes != null -> fieldValidator.validateScopes(
                subCtx, "$configKeyPrefix.default-scopes", parsed.defaultScopes
            )
            else -> parsed.template?.defaultScopes
        }

        // Validate webhook (already resolved with template fallback by parser).
        val authorizationWebhook = fieldValidator.validateWebhook(parsed.authorizationWebhook)

        ctx.merge(subCtx)
        if (subCtx.hasErrors) return null

        return Client(
            id = parsed.id,
            secret = parsed.secret,
            audience = audience!!,
            public = parsed.isPublic,
            allowedGrantTypes = allowedGrantTypes!!,
            authorizationFlow = authorizationFlow,
            allowedRedirectUris = allowedRedirectUris!!,
            allowedScopes = allowedScopes,
            defaultScopes = defaultScopes,
            authorizationWebhook = authorizationWebhook
        )
    }

    private fun validateAndResolveAudience(
        ctx: ConfigParsingContext,
        parsed: ParsedClient,
        audiencesById: Map<String, Audience>,
        configKeyPrefix: String
    ): Audience? {
        val audienceId = parsed.audienceId
        if (audienceId == null) {
            ctx.addError(configExceptionOf("$configKeyPrefix.audience", "config.client.audience.missing"))
            return null
        }
        val audience = audiencesById[audienceId]
        if (audience == null) {
            ctx.addError(
                configExceptionOf(
                    "$configKeyPrefix.audience",
                    "config.client.audience.not_found",
                    "audience" to audienceId,
                    "availableAudiences" to audiencesById.keys.joinToString(", ")
                )
            )
        }
        return audience
    }
}
