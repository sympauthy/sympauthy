package com.sympauthy.config.parsing

import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.ConfigTemplateResolver
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.properties.ClientAuthorizationWebhookProperties
import com.sympauthy.platform.util.enumValueOfWireNameOrNull
import com.sympauthy.platform.util.wireName
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
 * Parsing methods for the fields a client and the template it takes its defaults from both declare:
 * type conversion and template resolution for each of them.
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
            val grantType = enumValueOfWireNameOrNull<GrantType>(value)
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
     * Resolve template variables in a list of absolute URLs, such as the redirect URIs, and parse them.
     * [rootUri] is what the `urls.root` template variable resolves to.
     * Returns null when the list is empty or null.
     */
    fun parseUris(
        ctx: ConfigParsingContext,
        configKey: String,
        uris: Map<String, String>?,
        unparsedUris: List<String>?,
        rootUri: URI
    ): List<String>? {
        if (unparsedUris.isNullOrEmpty()) return null

        val templateContext = buildTemplateContext(uris, rootUri)
        return unparsedUris.mapIndexedNotNull { index, uri ->
            ctx.parse { resolveAbsoluteUri(uri, templateContext, "$configKey[$index]") }
        }.takeIf { !ctx.hasErrors }
    }

    /**
     * Resolve template variables in the absolute URL [unparsedUri], such as the return URI, and parse it.
     * [rootUri] is what the `urls.root` template variable resolves to.
     * Returns null when [unparsedUri] is null or invalid.
     */
    fun parseUri(
        ctx: ConfigParsingContext,
        configKey: String,
        uris: Map<String, String>?,
        unparsedUri: String?,
        rootUri: URI
    ): String? {
        if (unparsedUri == null) return null
        return ctx.parse { resolveAbsoluteUri(unparsedUri, buildTemplateContext(uris, rootUri), configKey) }
    }

    private fun resolveAbsoluteUri(uri: String, templateContext: Map<String, String>, configKey: String): String {
        val resolved = templateResolver.resolve(uri, templateContext, configKey)
        if (UriBuilder.of(resolved).build().scheme.isNullOrBlank()) {
            throw configExceptionOf(configKey, "config.invalid_url")
        }
        return resolved
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
