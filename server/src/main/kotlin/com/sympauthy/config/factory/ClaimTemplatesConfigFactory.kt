package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.model.ClaimTemplatesConfig
import com.sympauthy.config.model.DisabledClaimTemplatesConfig
import com.sympauthy.config.model.EnabledClaimTemplatesConfig
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ClaimTemplatesConfigFactory(
    @Inject private val parser: ConfigParser
) {

    @Singleton
    fun provideClaimTemplates(
        templatesList: List<ClaimTemplateConfigurationProperties>
    ): ClaimTemplatesConfig {
        val errors = mutableListOf<ConfigurationException>()

        val templates = templatesList.mapNotNull { properties ->
            getTemplate(properties, errors)
        }.associateBy { it.id }

        return if (errors.isEmpty()) {
            EnabledClaimTemplatesConfig(templates)
        } else {
            DisabledClaimTemplatesConfig(errors)
        }
    }

    private fun getTemplate(
        properties: ClaimTemplateConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): ClaimTemplate? {
        val templateErrors = mutableListOf<ConfigurationException>()
        val configKeyPrefix = "$TEMPLATES_CLAIMS_KEY.${properties.id}"

        val enabled = try {
            parser.getBoolean(
                properties, "$configKeyPrefix.enabled",
                ClaimTemplateConfigurationProperties::enabled
            )
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val required = try {
            parser.getBoolean(
                properties, "$configKeyPrefix.required",
                ClaimTemplateConfigurationProperties::required
            )
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val acl = properties.acl

        val readableByUserWhenConsented = try {
            acl?.readableByUserWhenConsented?.let {
                parser.getBoolean(
                    properties, "$configKeyPrefix.acl.readable-by-user-when-consented"
                ) { it }
            }
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val writableByUserWhenConsented = try {
            acl?.writableByUserWhenConsented?.let {
                parser.getBoolean(
                    properties, "$configKeyPrefix.acl.writable-by-user-when-consented"
                ) { it }
            }
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val readableByClientWhenConsented = try {
            acl?.readableByClientWhenConsented?.let {
                parser.getBoolean(
                    properties, "$configKeyPrefix.acl.readable-by-client-when-consented"
                ) { it }
            }
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val writableByClientWhenConsented = try {
            acl?.writableByClientWhenConsented?.let {
                parser.getBoolean(
                    properties, "$configKeyPrefix.acl.writable-by-client-when-consented"
                ) { it }
            }
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        return if (templateErrors.isEmpty()) {
            ClaimTemplate(
                id = properties.id,
                enabled = enabled,
                required = required,
                allowedValues = properties.allowedValues,
                consentScope = acl?.scopeWhenConsented,
                readableByUserWhenConsented = readableByUserWhenConsented,
                writableByUserWhenConsented = writableByUserWhenConsented,
                readableByClientWhenConsented = readableByClientWhenConsented,
                writableByClientWhenConsented = writableByClientWhenConsented,
                readableWithClientScopesUnconditionally = acl?.readableWithClientScopesUnconditionally,
                writableWithClientScopesUnconditionally = acl?.writableWithClientScopesUnconditionally
            )
        } else {
            errors.addAll(templateErrors)
            null
        }
    }
}
