package com.sympauthy.config.parsing

import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.ConfigTemplateResolver
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.properties.ClientConfigurationProperties.AuthorizationWebhookConfig
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
    @Inject private val urlsConfig: UrlsConfig,
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
            val grantType = GrantType.fromValueOrNull(value)
            if (grantType == null) {
                ctx.addError(
                    configExceptionOf(
                        itemKey, "config.client.allowed_grant_types.invalid",
                        "grantType" to value,
                        "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                    )
                )
            }
            grantType
        }.toSet()

        return if (ctx.hasErrors) null else parsed
    }

    /**
     * Resolve template variables in redirect URIs and parse them.
     * Returns null when the list is empty or null.
     */
    fun parseRedirectUris(
        ctx: ConfigParsingContext,
        configKey: String,
        uris: Map<String, String>?,
        allowedRedirectUris: List<String>?
    ): List<String>? {
        if (allowedRedirectUris.isNullOrEmpty()) return null

        val templateContext = buildTemplateContext(uris)
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
        webhookConfig: AuthorizationWebhookConfig?,
        templateWebhook: AuthorizationWebhook?
    ): ParsedAuthorizationWebhook? {
        if (webhookConfig == null && templateWebhook == null) return null

        val subCtx = ctx.child()
        val url = if (webhookConfig?.url != null) {
            subCtx.parse {
                parser.getAbsoluteUriOrThrow(webhookConfig, "$configKey.url", AuthorizationWebhookConfig::url)
            }
        } else {
            templateWebhook?.url
        }
        val secret = if (webhookConfig?.secret != null) {
            subCtx.parse {
                parser.getStringOrThrow(webhookConfig, "$configKey.secret", AuthorizationWebhookConfig::secret)
            }
        } else {
            templateWebhook?.secret
        }
        val onFailure = if (webhookConfig?.onFailure != null) {
            subCtx.parse {
                parser.getEnum(
                    webhookConfig, "$configKey.on-failure",
                    AuthorizationWebhookOnFailure.DENY_ALL,
                    AuthorizationWebhookConfig::onFailure
                )
            }
        } else {
            templateWebhook?.onFailure ?: AuthorizationWebhookOnFailure.DENY_ALL
        }
        ctx.merge(subCtx)
        if (subCtx.hasErrors) return null

        return ParsedAuthorizationWebhook(url = url, secret = secret, onFailure = onFailure)
    }

    private fun buildTemplateContext(uris: Map<String, String>?): Map<String, String> {
        val context = mutableMapOf<String, String>()
        val enabledUrlsConfig = urlsConfig as? EnabledUrlsConfig
        if (enabledUrlsConfig != null) {
            context["urls.root"] = enabledUrlsConfig.root.toString()
        }
        uris?.forEach { (key, value) ->
            context["client.uris.$key"] = value
        }
        return context
    }
}
