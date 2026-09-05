package com.sympauthy.config.parsing

import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.ConfigTemplateResolver
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.properties.ClientAuthorizationWebhookProperties
import com.sympauthy.util.wireName
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

data class ParsedAuthorizationWebhook(
    val url: URI?,
    val secret: String?,
    val onFailure: AuthorizationWebhookOnFailure?
)

/**
 * Shared parsing methods for client configuration fields.
 *
 * Used by both [ClientsConfigParser] and [ClientTemplatesConfigParser]
 * for type conversion and template resolution.
 */
@Singleton
class ClientConfigFieldParser(
    @Inject private val parser: ConfigParser,
    @Inject private val templateResolver: ConfigTemplateResolver
) {

    /**
     * Parse grant type strings into enum values.
     * Returns null when the list is empty or null.
     */
    fun parseGrantTypes(
        ctx: ConfigParsingContext,
        configKey: String,
        allowedGrantTypes: List<String>?
    ): Set<GrantType>? {
        if (allowedGrantTypes.isNullOrEmpty()) return null

        val parsed = allowedGrantTypes.mapIndexedNotNull { index, value ->
            val itemKey = "$configKey[$index]"
            val grantType = GrantType.fromWireNameOrNull(value)
            if (grantType == null) {
                ctx.addError(
                    configExceptionOf(
                        itemKey, "config.client.allowed_grant_types.invalid",
                        "grantType" to value,
                        "supportedValues" to GrantType.entries.joinToString(", ") { it.wireName }
                    )
                )
            }
            grantType
        }.toSet()

        return if (ctx.hasErrors) null else parsed
    }

    /**
     * Resolve template variables in redirect URIs and parse them.
     * [rootUri] is what the `urls.root` template variable resolves to.
     * Returns null when the list is empty or null.
     */
    fun parseRedirectUris(
        ctx: ConfigParsingContext,
        configKey: String,
        uris: Map<String, String>?,
        allowedRedirectUris: List<String>?,
        rootUri: URI
    ): List<String>? {
        if (allowedRedirectUris.isNullOrEmpty()) return null

        val templateContext = buildTemplateContext(uris, rootUri)
        return allowedRedirectUris.mapIndexedNotNull { index, uri ->
            val itemKey = "$configKey[$index]"
            ctx.parse {
                val resolved = templateResolver.resolve(uri, templateContext, itemKey)
                UriBuilder.of(resolved).build() // validate it's a parseable URI
                if (UriBuilder.of(resolved).build().scheme.isNullOrBlank()) {
                    throw configExceptionOf(itemKey, "config.invalid_url")
                }
                resolved
            }
        }.takeIf { !ctx.hasErrors }
    }

    /**
     * Parse webhook configuration fields with per-field template fallback.
     */
    fun parseWebhook(
        ctx: ConfigParsingContext,
        configKey: String,
        webhookConfig: ClientAuthorizationWebhookProperties?,
        templateWebhook: AuthorizationWebhook?
    ): ParsedAuthorizationWebhook? {
        if (webhookConfig == null && templateWebhook == null) return null

        val subCtx = ctx.child()
        val url = if (webhookConfig?.url != null) {
            subCtx.parse {
                parser.getAbsoluteUriOrThrow(
                    webhookConfig, "$configKey.url", ClientAuthorizationWebhookProperties::url
                )
            }
        } else {
            templateWebhook?.url
        }
        val secret = if (webhookConfig?.secret != null) {
            subCtx.parse {
                parser.getStringOrThrow(
                    webhookConfig, "$configKey.secret", ClientAuthorizationWebhookProperties::secret
                )
            }
        } else {
            templateWebhook?.secret
        }
        val onFailure = if (webhookConfig?.onFailure != null) {
            subCtx.parse {
                parser.getEnum(
                    webhookConfig, "$configKey.on-failure",
                    AuthorizationWebhookOnFailure.DENY_ALL,
                    ClientAuthorizationWebhookProperties::onFailure
                )
            }
        } else {
            templateWebhook?.onFailure ?: AuthorizationWebhookOnFailure.DENY_ALL
        }
        ctx.merge(subCtx)
        if (subCtx.hasErrors) return null

        return ParsedAuthorizationWebhook(url = url, secret = secret, onFailure = onFailure)
    }

    private fun buildTemplateContext(uris: Map<String, String>?, rootUri: URI): Map<String, String> {
        val context = mutableMapOf<String, String>("urls.root" to rootUri.toString())
        uris?.forEach { (key, value) ->
            context["client.uris.$key"] = value
        }
        return context
    }
}
