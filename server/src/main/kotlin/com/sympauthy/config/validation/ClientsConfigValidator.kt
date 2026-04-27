package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.factory.ClientConfigFieldParser
import com.sympauthy.config.parsing.ParsedClient
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClientsConfigValidator(
    @Inject private val fieldParser: ClientConfigFieldParser
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

        // Validate fields via ClientConfigFieldParser (temporarily mixing concerns).
        val errors = mutableListOf<ConfigurationException>()

        val allowedGrantTypes = try {
            when {
                parsed.allowedGrantTypes != null -> fieldParser.getAllowedGrantTypesOrNull(
                    configKey = "$configKeyPrefix.allowed-grant-types",
                    allowedGrantTypes = parsed.allowedGrantTypes,
                    errors = errors
                )
                parsed.template?.allowedGrantTypes != null -> parsed.template.allowedGrantTypes
                else -> {
                    errors.add(
                        configExceptionOf(
                            "$configKeyPrefix.allowed-grant-types",
                            "config.client.allowed_grant_types.missing",
                            "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                        )
                    )
                    null
                }
            }
        } catch (e: ConfigurationException) { errors.add(e); null }

        val authorizationFlow = try {
            when {
                parsed.authorizationFlowId != null -> fieldParser.getAuthorizationFlow(
                    key = "$configKeyPrefix.authorization-flow",
                    flowId = parsed.authorizationFlowId
                )
                else -> parsed.template?.authorizationFlow
            }
        } catch (e: ConfigurationException) { errors.add(e); null }

        val allowedRedirectUris = if (allowedGrantTypes?.contains(GrantType.AUTHORIZATION_CODE) != false) {
            try {
                when {
                    parsed.allowedRedirectUris != null -> fieldParser.getAllowedRedirectUrisOrNull(
                        configKey = "$configKeyPrefix.allowed-redirect-uris",
                        uris = parsed.uris,
                        allowedRedirectUris = parsed.allowedRedirectUris,
                        errors = errors
                    )
                    parsed.template?.allowedRedirectUris != null -> parsed.template.allowedRedirectUris
                    else -> {
                        errors.add(
                            configExceptionOf("$configKeyPrefix.allowed-redirect-uris", "config.client.allowed_redirect_uris.missing")
                        )
                        null
                    }
                }
            } catch (e: ConfigurationException) { errors.add(e); null }
        } else {
            if (!parsed.allowedRedirectUris.isNullOrEmpty()) {
                errors.add(
                    configExceptionOf("$configKeyPrefix.allowed-redirect-uris", "config.client.allowed_redirect_uris.unnecessary")
                )
            }
            emptyList()
        }

        val allowedScopes = try {
            when {
                parsed.allowedScopes != null -> fieldParser.getScopes(
                    key = "$configKeyPrefix.allowed-scopes", scopes = parsed.allowedScopes, errors = errors
                )?.toSet()
                else -> parsed.template?.allowedScopes
            }
        } catch (e: ConfigurationException) { errors.add(e); null }

        val defaultScopes = try {
            when {
                parsed.defaultScopes != null -> fieldParser.getScopes(
                    key = "$configKeyPrefix.default-scopes", scopes = parsed.defaultScopes, errors = errors
                )
                else -> parsed.template?.defaultScopes
            }
        } catch (e: ConfigurationException) { errors.add(e); null }

        val authorizationWebhook = try {
            when {
                parsed.authorizationWebhook != null -> fieldParser.getAuthorizationWebhook(
                    configKey = "$configKeyPrefix.authorization-webhook",
                    webhookConfig = parsed.authorizationWebhook,
                    errors = errors
                )
                else -> parsed.template?.authorizationWebhook
            }
        } catch (e: ConfigurationException) { errors.add(e); null }

        errors.forEach { subCtx.addError(it) }
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
