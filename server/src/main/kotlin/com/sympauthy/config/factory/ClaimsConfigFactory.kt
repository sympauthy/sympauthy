package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
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

        val generatedClaimIds = GeneratedOpenIdConnectClaim.entries.map { it.id }.toSet()

        // Generated OpenID claims have hardcoded configuration.
        val generatedClaims = GeneratedOpenIdConnectClaim.entries.map { generatedClaim ->
            provideGeneratedClaim(
                properties = propertiesList.firstOrNull { it.id.normalizeClaimId() == generatedClaim.id },
                generatedClaim = generatedClaim,
                templates = templates,
                errors = errors
            )
        }

        // All other claims (OpenID and custom) are configured the same way.
        val configurableClaims = propertiesList.mapNotNull { claimProperties ->
            val normalizedId = claimProperties.id.normalizeClaimId()
            // Skip generated claims — they are handled above.
            if (normalizedId in generatedClaimIds) return@mapNotNull null
            provideClaim(
                properties = claimProperties,
                templates = templates,
                errors = errors
            )
        }

        // Check if claims are properly configured for user merging.
        val allClaims = generatedClaims + configurableClaims
        if (authProperties.userMergingEnabled == true) {
            val identifierClaimIds = authProperties.identifierClaims ?: emptyList()
            val enabledClaimIds = allClaims.filter { it.enabled }.map { it.id }.toSet()
            identifierClaimIds.forEach { identifierClaimId ->
                if (identifierClaimId !in enabledClaimIds) {
                    errors.add(
                        configExceptionOf(
                            "$CLAIMS_KEY.$identifierClaimId",
                            "config.auth.identifier_claim.disabled",
                            "claim" to identifierClaimId
                        )
                    )
                }
            }
        }

        return if (errors.isEmpty()) {
            EnabledClaimsConfig(allClaims)
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
        generatedClaim: GeneratedOpenIdConnectClaim,
        templates: Map<String, ClaimTemplate>,
        errors: MutableList<ConfigurationException>
    ): Claim {
        val template = if (properties != null) {
            resolveTemplate(properties, templates, errors)
        } else {
            templates[DEFAULT]
        }
        val configKeyPrefix = "$CLAIMS_KEY.${generatedClaim.id}"

        val acl = claimAclFactory.buildGeneratedClaimAcl(
            acl = properties?.acl,
            template = template,
            configKeyPrefix = configKeyPrefix,
            consentScope = generatedClaim.scope,
            errors = errors
        )

        return Claim(
            id = generatedClaim.id,
            enabled = true,
            verifiedId = generatedClaim.verifiedId,
            dataType = generatedClaim.dataType,
            group = generatedClaim.group,
            required = false,
            generated = true,
            userInputted = false,
            allowedValues = null,
            acl = acl
        )
    }

    /**
     * Provide a configurable claim. Both OpenID Connect and custom claims share the same
     * configuration path — the claim's origin is derived from its ID at runtime.
     */
    private fun provideClaim(
        properties: ClaimConfigurationProperties,
        templates: Map<String, ClaimTemplate>,
        errors: MutableList<ConfigurationException>
    ): Claim? {
        val template = resolveTemplate(properties, templates, errors)
        val claimId = properties.id.normalizeClaimId()
        val configKeyPrefix = "$CLAIMS_KEY.$claimId"

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

        val group = try {
            properties.group?.let {
                parser.convertToEnum<ClaimGroup>("$configKeyPrefix.group", it)
            } ?: template?.group
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val verifiedId = properties.verifiedId

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
            id = claimId,
            enabled = enabled,
            verifiedId = verifiedId,
            dataType = dataType,
            group = group,
            required = required,
            generated = false,
            userInputted = acl.consent.writableByUser,
            allowedValues = allowedValues,
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
}
