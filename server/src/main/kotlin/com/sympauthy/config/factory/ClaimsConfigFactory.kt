package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.AuthConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.DEFAULT
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Micronaut normalizes property keys to kebab-case (e.g. `preferred_username` becomes `preferred-username`).
 * OpenID claim IDs use underscores. This function normalizes the Micronaut key back to match the OpenID ID.
 */
private fun String.normalizeClaimId() = replace('-', '_')

@Factory
class ClaimsConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val authProperties: AuthConfigurationProperties,
    @Inject private val claimTemplatesConfig: ClaimTemplatesConfig,
    @Inject private val claimAclFactory: ClaimAclFactory
) {

    @Singleton
    fun provideClaims(
        propertiesList: List<ClaimConfigurationProperties>
    ): ClaimsConfig {
        val enabledTemplatesConfig = claimTemplatesConfig.orNull()
            ?: return DisabledClaimsConfig(emptyList())
        val templates = enabledTemplatesConfig.templates

        val errors = mutableListOf<ConfigurationException>()

        val openIdClaims = OpenIdClaim.entries.map { openIdClaim ->
            if (openIdClaim.generated) {
                provideGeneratedClaim(
                    properties = propertiesList.firstOrNull { it.id.normalizeClaimId() == openIdClaim.id },
                    openIdClaim = openIdClaim,
                    templates = templates,
                    errors = errors
                )
            } else {
                provideOpenIdClaim(
                    properties = propertiesList.firstOrNull { it.id.normalizeClaimId() == openIdClaim.id },
                    openIdClaim = openIdClaim,
                    templates = templates,
                    errors = errors
                )
            }
        }

        val customClaims = propertiesList.mapNotNull { claimProperties ->
            if (OpenIdClaim.entries.none { it.id == claimProperties.id.normalizeClaimId() }) {
                provideCustomClaim(
                    properties = claimProperties,
                    claim = claimProperties.id,
                    templates = templates,
                    errors = errors
                )
            } else null
        }

        // Check if claims are properly configured for user merging.
        if (authProperties.userMergingEnabled == true) {
            val identifierClaimIds = authProperties.identifierClaims
                ?.mapNotNull { id -> OpenIdClaim.entries.firstOrNull { it.id == id } }
                ?: emptyList()
            val enabledClaimIds = openIdClaims.filter { it.enabled }.map { it.id }.toSet()
            identifierClaimIds.forEach { identifierClaim ->
                if (identifierClaim.id !in enabledClaimIds) {
                    errors.add(
                        configExceptionOf(
                            "$CLAIMS_KEY.${identifierClaim.id}",
                            "config.auth.identifier_claim.disabled",
                            "claim" to identifierClaim.id
                        )
                    )
                }
            }
        }

        return if (errors.isEmpty()) {
            EnabledClaimsConfig(openIdClaims + customClaims)
        } else {
            DisabledClaimsConfig(errors)
        }
    }

    /**
     * Generated claims (e.g. `sub`, `updated_at`) have server-managed values.
     * They are always enabled, read-only, and gated by their OpenID scope.
     * Only [com.sympauthy.business.model.user.claim.UnconditionalAcl.readableWithClientScopes]
     * is configurable via YAML/templates.
     */
    private fun provideGeneratedClaim(
        properties: ClaimConfigurationProperties?,
        openIdClaim: OpenIdClaim,
        templates: Map<String, ClaimTemplate>,
        errors: MutableList<ConfigurationException>
    ): Claim {
        val template = if (properties != null) {
            resolveTemplate(properties, templates, errors)
        } else {
            templates[DEFAULT]
        }
        val configKeyPrefix = "$CLAIMS_KEY.${openIdClaim.id}"

        val acl = claimAclFactory.buildGeneratedClaimAcl(
            acl = properties?.acl,
            template = template,
            configKeyPrefix = configKeyPrefix,
            consentScope = openIdClaim.scope.scope,
            errors = errors
        )

        return Claim(
            id = openIdClaim.id,
            enabled = true,
            verifiedId = openIdClaim.verifiedId,
            dataType = openIdClaim.type,
            group = openIdClaim.group,
            required = false,
            generated = true,
            userInputted = false,
            allowedValues = null,
            acl = acl
        )
    }

    private fun resolveTemplate(
        properties: ClaimConfigurationProperties,
        templates: Map<String, ClaimTemplate>,
        errors: MutableList<ConfigurationException>
    ): ClaimTemplate? {
        val templateName = properties.template
        if (templateName != null) {
            if (templateName == DEFAULT) {
                errors.add(
                    configExceptionOf(
                        "$CLAIMS_KEY.${properties.id}.template",
                        "config.claim.template.cannot_reference_default"
                    )
                )
                return null
            }
            val template = templates[templateName]
            if (template == null) {
                errors.add(
                    configExceptionOf(
                        "$CLAIMS_KEY.${properties.id}.template",
                        "config.claim.template.not_found",
                        "template" to templateName,
                        "claim" to properties.id,
                        "availableTemplates" to templates.keys
                            .filter { it != DEFAULT }
                            .joinToString(", ")
                    )
                )
                return null
            }
            return template
        }
        return templates[DEFAULT]
    }

    private fun provideOpenIdClaim(
        properties: ClaimConfigurationProperties?,
        openIdClaim: OpenIdClaim,
        templates: Map<String, ClaimTemplate>,
        errors: MutableList<ConfigurationException>
    ): Claim {
        val template = if (properties != null) {
            resolveTemplate(properties, templates, errors)
        } else {
            templates[DEFAULT]
        }
        val configKeyPrefix = "$CLAIMS_KEY.${openIdClaim.id}"

        val enabled = try {
            properties?.let {
                parser.getBoolean(
                    it, "$configKeyPrefix.enabled",
                    ClaimConfigurationProperties::enabled
                )
            } ?: template?.enabled ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            false
        }

        val required = try {
            properties?.let {
                parser.getBoolean(
                    it, "$configKeyPrefix.required",
                    ClaimConfigurationProperties::required
                )
            } ?: template?.required ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            false
        }

        val allowedValues = if (properties != null) {
            properties.allowedValues?.let {
                getAllowedValues(
                    properties = properties,
                    key = "$configKeyPrefix.allowed-values",
                    type = openIdClaim.type,
                    errors = errors
                )
            } ?: template?.allowedValues
        } else {
            template?.allowedValues
        }

        val acl = claimAclFactory.buildAcl(
            acl = properties?.acl,
            template = template,
            configKeyPrefix = configKeyPrefix,
            defaultConsentScope = openIdClaim.scope.scope,
            errors = errors
        )

        return Claim(
            id = openIdClaim.id,
            enabled = enabled,
            verifiedId = openIdClaim.verifiedId,
            dataType = openIdClaim.type,
            group = openIdClaim.group,
            required = required,
            generated = openIdClaim.generated,
            userInputted = !openIdClaim.generated && acl.consent.writableByUser,
            allowedValues = allowedValues,
            acl = acl
        )
    }

    private fun getAllowedValues(
        properties: ClaimConfigurationProperties,
        key: String,
        type: ClaimDataType,
        errors: MutableList<ConfigurationException>
    ): List<Any>? {
        return properties.allowedValues?.mapIndexedNotNull { index, value ->
            val itemKey = "${key}[${index}]"
            try {
                when (type.typeClass) {
                    String::class -> parser.getString(properties, itemKey) { value }
                    else -> throw configExceptionOf(
                        itemKey, "config.claim.allowed_values.invalid_type",
                        "type" to type.typeClass.simpleName
                    )
                }
            } catch (e: ConfigurationException) {
                errors.add(e)
                null
            }
        }
    }

    private fun provideCustomClaim(
        properties: ClaimConfigurationProperties,
        claim: String,
        templates: Map<String, ClaimTemplate>,
        errors: MutableList<ConfigurationException>
    ): Claim? {
        val template = resolveTemplate(properties, templates, errors)
        val configKeyPrefix = "$CLAIMS_KEY.$claim"

        val dataType: ClaimDataType = try {
            parser.getEnumOrThrow(
                properties, "$configKeyPrefix.type",
            ) { properties.type }
        } catch (e: ConfigurationException) {
            errors.add(e)
            return null
        }

        val enabled = try {
            parser.getBoolean(
                properties, "$configKeyPrefix.enabled",
                ClaimConfigurationProperties::enabled
            ) ?: template?.enabled ?: true
        } catch (e: ConfigurationException) {
            errors.add(e)
            true
        }

        val required = try {
            parser.getBoolean(
                properties, "$configKeyPrefix.required",
                ClaimConfigurationProperties::required
            ) ?: template?.required ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            false
        }

        val allowedValues = properties.allowedValues?.let {
            getAllowedValues(
                properties = properties,
                key = "$configKeyPrefix.allowed-values",
                type = dataType,
                errors = errors
            )
        } ?: template?.allowedValues

        val acl = claimAclFactory.buildAcl(
            acl = properties.acl,
            template = template,
            configKeyPrefix = configKeyPrefix,
            defaultConsentScope = null,
            errors = errors
        )

        return Claim(
            id = claim,
            enabled = enabled,
            verifiedId = null,
            dataType = dataType,
            group = null,
            required = required,
            generated = false,
            userInputted = acl.consent.writableByUser,
            allowedValues = allowedValues,
            acl = acl
        )
    }
}
