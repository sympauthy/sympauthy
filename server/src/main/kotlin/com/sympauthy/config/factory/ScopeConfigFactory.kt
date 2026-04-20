package com.sympauthy.config.factory

import com.sympauthy.business.model.oauth2.isAdminScope
import com.sympauthy.business.model.oauth2.isBuiltInClientScope
import com.sympauthy.business.model.oauth2.isBuiltInGrantableScope
import com.sympauthy.business.model.user.isOpenIdConnectScope
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.ScopeConfigurationProperties
import com.sympauthy.config.properties.ScopeConfigurationProperties.Companion.SCOPES_KEY
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.DEFAULT_CUSTOM
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.DEFAULT_OPENID
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.DEFAULT_TEMPLATE_NAMES
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ScopeConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val scopeTemplatesConfig: ScopeTemplatesConfig
) {

    @Singleton
    fun provideScopes(
        propertiesList: List<ScopeConfigurationProperties>
    ): ScopesConfig {
        val enabledTemplatesConfig = scopeTemplatesConfig.orNull()
            ?: return DisabledScopesConfig(emptyList())
        val templates = enabledTemplatesConfig.templates

        val errors = mutableListOf<ConfigurationException>()

        val scopes = propertiesList.mapNotNull { properties ->
            when {
                properties.id.isAdminScope() -> {
                    errors.add(configExceptionOf(
                        "$SCOPES_KEY.${properties.id}",
                        "config.scope.admin_not_configurable",
                        "scope" to properties.id
                    ))
                    null
                }
                properties.id.isBuiltInGrantableScope() -> {
                    errors.add(configExceptionOf(
                        "$SCOPES_KEY.${properties.id}",
                        "config.scope.builtin_not_configurable",
                        "scope" to properties.id
                    ))
                    null
                }
                properties.id.isBuiltInClientScope() -> {
                    errors.add(configExceptionOf(
                        "$SCOPES_KEY.${properties.id}",
                        "config.scope.builtin_not_configurable",
                        "scope" to properties.id
                    ))
                    null
                }
                properties.id.isOpenIdConnectScope() -> {
                    val template = resolveTemplate(properties, templates, DEFAULT_OPENID, errors)
                    getOpenIdConnectScope(properties = properties, template = template, errors = errors)
                }
                else -> {
                    val template = resolveTemplate(properties, templates, DEFAULT_CUSTOM, errors)
                    getCustomScope(properties = properties, template = template, errors = errors)
                }
            }
        }

        return if (errors.isEmpty()) {
            EnabledScopesConfig(scopes)
        } else {
            DisabledScopesConfig(errors)
        }
    }

    private fun resolveTemplate(
        properties: ScopeConfigurationProperties,
        templates: Map<String, ScopeTemplate>,
        defaultTemplateName: String,
        errors: MutableList<ConfigurationException>
    ): ScopeTemplate? {
        val templateName = properties.template
        if (templateName != null) {
            if (templateName in DEFAULT_TEMPLATE_NAMES) {
                errors.add(
                    configExceptionOf(
                        "$SCOPES_KEY.${properties.id}.template",
                        "config.scope.template.cannot_reference_default",
                        "defaultTemplates" to DEFAULT_TEMPLATE_NAMES.joinToString(", ")
                    )
                )
                return null
            }
            val template = templates[templateName]
            if (template == null) {
                errors.add(
                    configExceptionOf(
                        "$SCOPES_KEY.${properties.id}.template",
                        "config.scope.template.not_found",
                        "template" to templateName,
                        "scope" to properties.id,
                        "availableTemplates" to templates.keys.filter { it !in DEFAULT_TEMPLATE_NAMES }.joinToString(", ")
                    )
                )
                return null
            }
            return template
        }
        return templates[defaultTemplateName]
    }

    private fun getOpenIdConnectScope(
        properties: ScopeConfigurationProperties,
        template: ScopeTemplate?,
        errors: MutableList<ConfigurationException>
    ): ScopeConfig? {
        val scopeErrors = mutableListOf<ConfigurationException>()

        val enabled = try {
            parser.getBoolean(
                properties, "$SCOPES_KEY.${properties.id}.enabled",
                ScopeConfigurationProperties::enabled
            ) ?: template?.enabled ?: true
        } catch (e: ConfigurationException) {
            scopeErrors.add(e)
            null
        }

        return if (scopeErrors.isEmpty()) {
            OpenIdConnectScopeConfig(
                scope = properties.id,
                enabled = enabled!!
            )
        } else {
            errors.addAll(scopeErrors)
            null
        }
    }

    private fun getCustomScope(
        properties: ScopeConfigurationProperties,
        template: ScopeTemplate?,
        errors: MutableList<ConfigurationException>
    ): ScopeConfig? {
        val scopeErrors = mutableListOf<ConfigurationException>()

        val type = (properties.type ?: template?.type)?.lowercase()
        val consentable = when (type) {
            null, "grantable" -> false
            "consentable" -> true
            "client" -> {
                scopeErrors.add(configExceptionOf(
                    "$SCOPES_KEY.${properties.id}.type",
                    "config.scope.custom_client_type_not_allowed",
                    "scope" to properties.id
                ))
                null
            }
            else -> {
                scopeErrors.add(configExceptionOf(
                    "$SCOPES_KEY.${properties.id}.type",
                    "config.scope.invalid_type",
                    "scope" to properties.id,
                    "type" to type
                ))
                null
            }
        }

        return if (scopeErrors.isEmpty()) {
            CustomScopeConfig(
                scope = properties.id,
                consentable = consentable!!
            )
        } else {
            errors.addAll(scopeErrors)
            null
        }
    }
}
