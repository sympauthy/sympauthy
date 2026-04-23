package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.DisabledScopeTemplatesConfig
import com.sympauthy.config.model.EnabledScopeTemplatesConfig
import com.sympauthy.config.model.ScopeTemplate
import com.sympauthy.config.model.ScopeTemplatesConfig
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.TEMPLATES_SCOPES_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ScopeTemplatesConfigFactory(
    @Inject private val parser: ConfigParser
) {

    @Singleton
    fun provideScopeTemplates(
        templatesList: List<ScopeTemplateConfigurationProperties>
    ): ScopeTemplatesConfig {
        val errors = mutableListOf<ConfigurationException>()

        val templates = templatesList.mapNotNull { properties ->
            getTemplate(properties, errors)
        }.associateBy { it.id }

        return if (errors.isEmpty()) {
            EnabledScopeTemplatesConfig(templates)
        } else {
            DisabledScopeTemplatesConfig(errors)
        }
    }

    private fun getTemplate(
        properties: ScopeTemplateConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): ScopeTemplate? {
        val templateErrors = mutableListOf<ConfigurationException>()
        val configKeyPrefix = "$TEMPLATES_SCOPES_KEY.${properties.id}"

        val enabled = try {
            parser.getBoolean(
                properties, "$configKeyPrefix.enabled",
                ScopeTemplateConfigurationProperties::enabled
            )
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val type = properties.type?.lowercase()
        if (type != null) {
            val validTypes = setOf("consentable", "grantable", "client")
            if (type !in validTypes) {
                templateErrors.add(
                    configExceptionOf(
                        "$configKeyPrefix.type",
                        "config.scope.invalid_type",
                        "scope" to properties.id,
                        "type" to type
                    )
                )
            }
        }

        return if (templateErrors.isEmpty()) {
            ScopeTemplate(
                id = properties.id,
                enabled = enabled,
                type = type,
                audience = properties.audience
            )
        } else {
            errors.addAll(templateErrors)
            null
        }
    }
}
