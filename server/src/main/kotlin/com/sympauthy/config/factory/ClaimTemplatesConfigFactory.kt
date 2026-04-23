package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.ClaimGroup
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
    @Inject private val parser: ConfigParser,
    @Inject private val claimAclFactory: ClaimAclFactory
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

        val group = try {
            properties.group?.let {
                parser.convertToEnum<ClaimGroup>("$configKeyPrefix.group", it)
            }
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val acl = claimAclFactory.buildTemplateAcl(
            acl = properties.acl,
            configKeyPrefix = configKeyPrefix,
            errors = templateErrors
        )

        return if (templateErrors.isEmpty()) {
            ClaimTemplate(
                id = properties.id,
                enabled = enabled,
                required = required,
                group = group,
                allowedValues = properties.allowedValues,
                acl = acl
            )
        } else {
            errors.addAll(templateErrors)
            null
        }
    }
}
