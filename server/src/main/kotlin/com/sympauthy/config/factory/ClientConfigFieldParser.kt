package com.sympauthy.config.factory

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigTemplateResolver
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.properties.ClientConfigurationProperties.AuthorizationWebhookConfig
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Shared validation methods for client configuration fields.
 *
 * Used by both [ClientsConfigFactory] and [ClientTemplatesConfigFactory] to avoid duplicating
 * validation logic between client and template configurations.
 */
@Singleton
class ClientConfigFieldParser(
    @Inject private val parser: ConfigParser,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val authorizationFlowManager: AuthorizationFlowManager,
    @Inject private val urlsConfig: UrlsConfig,
    @Inject private val templateResolver: ConfigTemplateResolver
) {

    fun getAllowedGrantTypes(
        configKey: String,
        allowedGrantTypes: List<String>?,
        errors: MutableList<ConfigurationException>
    ): Set<GrantType>? {
        if (allowedGrantTypes.isNullOrEmpty()) {
            errors.add(
                configExceptionOf(
                    configKey, "config.client.allowed_grant_types.missing",
                    "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                )
            )
            return null
        }

        val grantTypeErrors = mutableListOf<ConfigurationException>()
        val parsed = allowedGrantTypes.mapIndexedNotNull { index, value ->
            val itemKey = "$configKey[$index]"
            val grantType = GrantType.fromValueOrNull(value)
            if (grantType == null) {
                grantTypeErrors.add(
                    configExceptionOf(
                        itemKey, "config.client.allowed_grant_types.invalid",
                        "grantType" to value,
                        "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                    )
                )
            }
            grantType
        }.toSet()

        if (grantTypeErrors.isNotEmpty()) {
            errors.addAll(grantTypeErrors)
            return null
        }

        if (GrantType.REFRESH_TOKEN in parsed && GrantType.AUTHORIZATION_CODE !in parsed) {
            errors.add(
                configExceptionOf(
                    configKey, "config.client.allowed_grant_types.refresh_token_requires_authorization_code"
                )
            )
            return null
        }

        return parsed
    }

    /**
     * Validates grant types leniently for templates: does not require the list to be non-empty.
     * Only validates individual values and grant type constraints if present.
     */
    fun validateGrantTypes(
        configKey: String,
        allowedGrantTypes: List<String>?,
        errors: MutableList<ConfigurationException>
    ): Set<GrantType>? {
        if (allowedGrantTypes.isNullOrEmpty()) {
            return null
        }

        val grantTypeErrors = mutableListOf<ConfigurationException>()
        val parsed = allowedGrantTypes.mapIndexedNotNull { index, value ->
            val itemKey = "$configKey[$index]"
            val grantType = GrantType.fromValueOrNull(value)
            if (grantType == null) {
                grantTypeErrors.add(
                    configExceptionOf(
                        itemKey, "config.client.allowed_grant_types.invalid",
                        "grantType" to value,
                        "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                    )
                )
            }
            grantType
        }.toSet()

        if (grantTypeErrors.isNotEmpty()) {
            errors.addAll(grantTypeErrors)
            return null
        }

        if (GrantType.REFRESH_TOKEN in parsed && GrantType.AUTHORIZATION_CODE !in parsed) {
            errors.add(
                configExceptionOf(
                    configKey, "config.client.allowed_grant_types.refresh_token_requires_authorization_code"
                )
            )
            return null
        }

        return parsed
    }

    fun getAuthorizationFlow(
        key: String,
        flowId: String?
    ): AuthorizationFlow? {
        return flowId?.let {
            authorizationFlowManager.findByIdOrNull(it) ?: throw configExceptionOf(
                key, "config.client.authorization_flow.invalid",
                "flow" to flowId
            )
        }
    }

    fun buildTemplateContext(
        uris: Map<String, String>?
    ): Map<String, String> {
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

    fun getAllowedRedirectUris(
        configKey: String,
        uris: Map<String, String>?,
        allowedRedirectUris: List<String>?,
        errors: MutableList<ConfigurationException>
    ): List<String>? {
        if (allowedRedirectUris.isNullOrEmpty()) {
            errors.add(configExceptionOf(configKey, "config.client.allowed_redirect_uris.missing"))
            return null
        }

        return validateRedirectUris(configKey, uris, allowedRedirectUris, errors)
    }

    /**
     * Validates redirect URIs leniently for templates: does not require the list to be non-empty.
     * Only validates individual URI values if present.
     */
    fun validateRedirectUris(
        configKey: String,
        uris: Map<String, String>?,
        allowedRedirectUris: List<String>?,
        errors: MutableList<ConfigurationException>
    ): List<String>? {
        if (allowedRedirectUris.isNullOrEmpty()) {
            return null
        }

        val listErrors = mutableListOf<ConfigurationException>()
        val templateContext = buildTemplateContext(uris)

        val resolvedUris = allowedRedirectUris.mapIndexedNotNull { index, uri ->
            val itemKey = "$configKey[$index]"
            try {
                val resolved = templateResolver.resolve(uri, templateContext, itemKey)
                val parsedUri = UriBuilder.of(resolved).build()
                if (parsedUri.scheme.isNullOrBlank()) {
                    throw configExceptionOf(itemKey, "config.invalid_url")
                }
                resolved
            } catch (e: ConfigurationException) {
                listErrors.add(e)
                null
            }
        }

        return if (listErrors.isEmpty()) {
            resolvedUris
        } else {
            errors.addAll(listErrors)
            null
        }
    }

    fun getAuthorizationWebhook(
        configKey: String,
        webhookConfig: AuthorizationWebhookConfig?,
        errors: MutableList<ConfigurationException>
    ): AuthorizationWebhook? {
        if (webhookConfig == null) {
            return null
        }

        val webhookErrors = mutableListOf<ConfigurationException>()

        val url = try {
            parser.getAbsoluteUriOrThrow(
                webhookConfig, "$configKey.url",
                AuthorizationWebhookConfig::url
            )
        } catch (e: ConfigurationException) {
            webhookErrors.add(e)
            null
        }

        val secret = try {
            parser.getStringOrThrow(
                webhookConfig, "$configKey.secret",
                AuthorizationWebhookConfig::secret
            )
        } catch (e: ConfigurationException) {
            webhookErrors.add(e)
            null
        }

        val onFailure = try {
            parser.getEnum(
                webhookConfig, "$configKey.on-failure",
                AuthorizationWebhookOnFailure.DENY_ALL,
                AuthorizationWebhookConfig::onFailure
            )
        } catch (e: ConfigurationException) {
            webhookErrors.add(e)
            null
        }

        return if (webhookErrors.isEmpty()) {
            AuthorizationWebhook(
                url = url!!,
                secret = secret!!,
                onFailure = onFailure!!,
            )
        } else {
            errors.addAll(webhookErrors)
            null
        }
    }

    suspend fun getScopes(
        key: String,
        scopes: List<String>?,
        errors: MutableList<ConfigurationException>
    ): List<Scope>? {
        val scopeErrors = mutableListOf<ConfigurationException>()

        val verifiedScopes = scopes?.mapIndexedNotNull { index, scope ->
            try {
                val verifiedScope = scopeManager.find(scope)
                if (verifiedScope == null) {
                    val error = configExceptionOf(
                        "$key[$index]", "config.client.scope.invalid",
                        "scope" to scope
                    )
                    scopeErrors.add(error)
                }
                verifiedScope
            } catch (t: Throwable) {
                // We do not add the error to the list since it is most likely already caused by another configuration error
                null
            }
        }

        return if (scopeErrors.isEmpty()) {
            verifiedScopes
        } else {
            errors.addAll(scopeErrors)
            null
        }
    }
}
