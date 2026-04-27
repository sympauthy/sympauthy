package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.factory.ClientConfigFieldParser
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.parsing.ParsedClientTemplate
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.TEMPLATES_CLIENTS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClientTemplatesConfigValidator(
    @Inject private val fieldParser: ClientConfigFieldParser
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

        // ClientConfigFieldParser methods mix parsing and validation.
        // They will be properly split in a later step.
        val errors = mutableListOf<ConfigurationException>()

        val allowedGrantTypes = try {
            fieldParser.getAllowedGrantTypesOrNull(
                configKey = "$configKeyPrefix.allowed-grant-types",
                allowedGrantTypes = parsed.allowedGrantTypes,
                errors = errors
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val authorizationFlow = try {
            fieldParser.getAuthorizationFlow(
                key = "$configKeyPrefix.authorization-flow",
                flowId = parsed.authorizationFlowId
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val allowedRedirectUris = try {
            fieldParser.getAllowedRedirectUrisOrNull(
                configKey = "$configKeyPrefix.allowed-redirect-uris",
                uris = parsed.uris,
                allowedRedirectUris = parsed.allowedRedirectUris,
                errors = errors
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val allowedScopes = try {
            fieldParser.getScopes(
                key = "$configKeyPrefix.allowed-scopes",
                scopes = parsed.allowedScopes,
                errors = errors
            )?.toSet()
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val defaultScopes = try {
            fieldParser.getScopes(
                key = "$configKeyPrefix.default-scopes",
                scopes = parsed.defaultScopes,
                errors = errors
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val authorizationWebhook = try {
            fieldParser.getAuthorizationWebhook(
                configKey = "$configKeyPrefix.authorization-webhook",
                webhookConfig = parsed.authorizationWebhook,
                errors = errors
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        errors.forEach { subCtx.addError(it) }
        ctx.merge(subCtx)
        if (subCtx.hasErrors) return null

        return ClientTemplate(
            id = parsed.id,
            audienceId = parsed.audienceId,
            public = parsed.isPublic,
            allowedGrantTypes = allowedGrantTypes,
            authorizationFlow = authorizationFlow,
            allowedRedirectUris = allowedRedirectUris,
            allowedScopes = allowedScopes,
            defaultScopes = defaultScopes,
            authorizationWebhook = authorizationWebhook
        )
    }
}
