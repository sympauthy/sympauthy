package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedClient
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClientsConfigValidator(
    @Inject private val fieldValidator: ClientConfigFieldValidator
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClient>,
        audiencesById: Map<String, Audience>,
        scopesById: Map<String, EnabledScope>,
        flowsById: Map<String, AuthorizationFlow>
    ): List<Client> {
        return parsed.mapNotNull { client ->
            validateClient(ctx, client, audiencesById, scopesById, flowsById)
        }
    }

    private fun validateClient(
        ctx: ConfigParsingContext,
        parsed: ParsedClient,
        audiencesById: Map<String, Audience>,
        scopesById: Map<String, EnabledScope>,
        flowsById: Map<String, AuthorizationFlow>
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
                    "supportedValues" to GrantType.entries.joinToString(", ") { it.wireName }
                )
            )
            null
        }

        // Validate authorization flow.
        val authorizationFlow = when {
            parsed.authorizationFlowId != null -> fieldValidator.validateAuthorizationFlow(
                subCtx, "$configKeyPrefix.authorization-flow", parsed.authorizationFlowId, flowsById
            )

            else -> parsed.template?.authorizationFlow
        }

        // Validate redirect URIs (already resolved with template fallback by parser).
        val allowedRedirectUris = if (allowedGrantTypes?.contains(GrantType.AUTHORIZATION_CODE) != false) {
            if (parsed.allowedRedirectUris != null) {
                parsed.allowedRedirectUris
            } else {
                subCtx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.allowed-redirect-uris",
                        "config.client.allowed_redirect_uris.missing"
                    )
                )
                null
            }
        } else {
            if (parsed.hasExplicitRedirectUris) {
                subCtx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.allowed-redirect-uris",
                        "config.client.allowed_redirect_uris.unnecessary"
                    )
                )
            }
            emptyList()
        }

        // Validate scopes.
        val allowedScopes = when {
            parsed.allowedScopes != null -> fieldValidator.validateScopes(
                subCtx, "$configKeyPrefix.allowed-scopes", parsed.allowedScopes, scopesById,
                audienceId = parsed.audienceId
            )?.toSet()

            else -> parsed.template?.allowedScopes
        }
        val defaultScopes = when {
            parsed.defaultScopes != null -> fieldValidator.validateScopes(
                subCtx, "$configKeyPrefix.default-scopes", parsed.defaultScopes, scopesById,
                audienceId = parsed.audienceId
            )

            else -> parsed.template?.defaultScopes
        }
        validateDefaultScopesAreAllowed(subCtx, configKeyPrefix, parsed, allowedScopes, defaultScopes)

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

    /**
     * Refuse the client when one of its [defaultScopes] is outside its [allowedScopes].
     *
     * The default scopes are what a request naming no scope at all is granted, and nothing on that
     * path puts them through the allowed set, so two lines disagreeing hand the client a scope the
     * same file says it may not have. Which of the two the operator meant cannot be read off either
     * of them, so the contradiction is refused here rather than resolved — `docs/design-faq.md`
     * carries why.
     *
     * A client allowing every scope has nothing to contradict, and so does one defaulting to none.
     */
    private fun validateDefaultScopesAreAllowed(
        ctx: ConfigParsingContext,
        configKeyPrefix: String,
        parsed: ParsedClient,
        allowedScopes: Set<EnabledScope>?,
        defaultScopes: List<EnabledScope>?
    ) {
        if (allowedScopes == null || defaultScopes == null) return
        // Where the client named no defaults of its own, the ones being refused came from a
        // template, and the operator has to be told which: the line is not one they wrote.
        val templateId = parsed.template?.id.takeIf { parsed.defaultScopes == null }
        val allowed = allowedScopes.joinToString(", ") { it.scope }
        defaultScopes.filter { it !in allowedScopes }.forEach { scope ->
            val error = if (templateId == null) {
                configExceptionOf(
                    "$configKeyPrefix.default-scopes",
                    "config.client.default_scopes.not_allowed",
                    "scope" to scope.scope,
                    "allowedScopes" to allowed
                )
            } else {
                configExceptionOf(
                    "$configKeyPrefix.default-scopes",
                    "config.client.default_scopes.not_allowed_from_template",
                    "scope" to scope.scope,
                    "template" to templateId,
                    "allowedScopes" to allowed
                )
            }
            ctx.addError(error)
        }
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
