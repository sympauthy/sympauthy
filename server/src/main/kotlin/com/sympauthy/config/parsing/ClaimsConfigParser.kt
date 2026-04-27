package com.sympauthy.config.parsing

import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.properties.ClaimAclProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.DEFAULT
import jakarta.inject.Singleton

/**
 * Micronaut normalizes property keys to kebab-case (e.g. `preferred_username` becomes `preferred-username`).
 * OpenID claim IDs use underscores. This function normalizes the Micronaut key back to match the OpenID ID.
 */
private fun String.normalizeClaimId() = replace('-', '_')

data class ParsedClaim(
    val id: String,
    val enabled: Boolean,
    val dataType: ClaimDataType?,
    val group: ClaimGroup?,
    val required: Boolean,
    val generated: Boolean,
    val verifiedId: String?,
    val audienceId: String?,
    val allowedValues: List<Any>?,
    val acl: ClaimAclProperties?
)

@Singleton
class ClaimsConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        propertiesList: List<ClaimConfigurationProperties>,
        templates: Map<String, ClaimTemplate>
    ): List<ParsedClaim> {
        val generatedClaimIds = GeneratedOpenIdConnectClaim.entries.map { it.id }.toSet()

        val generatedClaims = GeneratedOpenIdConnectClaim.entries.map { generatedClaim ->
            parseGeneratedClaim(
                ctx,
                properties = propertiesList.firstOrNull { it.id.normalizeClaimId() == generatedClaim.id },
                generatedClaim = generatedClaim,
                templates = templates
            )
        }

        val configurableClaims = propertiesList.mapNotNull { properties ->
            val normalizedId = properties.id.normalizeClaimId()
            if (normalizedId in generatedClaimIds) return@mapNotNull null
            parseClaim(ctx, properties, templates)
        }

        return generatedClaims + configurableClaims
    }

    private fun parseGeneratedClaim(
        ctx: ConfigParsingContext,
        properties: ClaimConfigurationProperties?,
        generatedClaim: GeneratedOpenIdConnectClaim,
        templates: Map<String, ClaimTemplate>
    ): ParsedClaim {
        val template = if (properties != null) {
            resolveTemplate(ctx, properties, templates)
        } else {
            templates[DEFAULT]
        }

        return ParsedClaim(
            id = generatedClaim.id,
            enabled = true,
            dataType = generatedClaim.dataType,
            group = generatedClaim.group,
            required = false,
            generated = true,
            verifiedId = generatedClaim.verifiedId,
            audienceId = null,
            allowedValues = null,
            acl = properties?.acl
        )
    }

    private fun parseClaim(
        ctx: ConfigParsingContext,
        properties: ClaimConfigurationProperties,
        templates: Map<String, ClaimTemplate>
    ): ParsedClaim? {
        val template = resolveTemplate(ctx, properties, templates)
        val claimId = properties.id.normalizeClaimId()
        val configKeyPrefix = "$CLAIMS_KEY.$claimId"

        val dataType: ClaimDataType? = ctx.parse {
            parser.getEnumOrThrow(properties, "$configKeyPrefix.type") { properties.type }
        }
        if (dataType == null) return null

        val enabled = ctx.parse {
            parser.getBoolean(properties, "$configKeyPrefix.enabled", ClaimConfigurationProperties::enabled)
        } ?: template?.enabled ?: true

        val required = ctx.parse {
            parser.getBoolean(properties, "$configKeyPrefix.required", ClaimConfigurationProperties::required)
        } ?: template?.required ?: false

        val group = ctx.parse {
            properties.group?.let {
                parser.convertToEnum<ClaimGroup>("$configKeyPrefix.group", it)
            }
        } ?: template?.group

        val audienceId = properties.audience ?: template?.audienceId

        val allowedValues = if (properties.allowedValues != null) {
            parseAllowedValues(ctx, properties, configKeyPrefix, dataType)
        } else {
            template?.allowedValues
        }

        return ParsedClaim(
            id = claimId,
            enabled = enabled,
            dataType = dataType,
            group = group,
            required = required,
            generated = false,
            verifiedId = properties.verifiedId,
            audienceId = audienceId,
            allowedValues = allowedValues,
            acl = properties.acl
        )
    }

    private fun parseAllowedValues(
        ctx: ConfigParsingContext,
        properties: ClaimConfigurationProperties,
        configKeyPrefix: String,
        type: ClaimDataType
    ): List<Any>? {
        val key = "$configKeyPrefix.allowed-values"
        return properties.allowedValues?.mapIndexedNotNull { index, value ->
            val itemKey = "${key}[$index]"
            ctx.parse {
                when (type.typeClass) {
                    String::class -> parser.getString(properties, itemKey) { value }
                    else -> throw configExceptionOf(
                        itemKey, "config.claim.allowed_values.invalid_type",
                        "type" to type.typeClass.simpleName
                    )
                }
            }
        }
    }

    private fun resolveTemplate(
        ctx: ConfigParsingContext,
        properties: ClaimConfigurationProperties,
        templates: Map<String, ClaimTemplate>
    ): ClaimTemplate? {
        val templateName = properties.template
        if (templateName != null) {
            if (templateName == DEFAULT) {
                ctx.addError(
                    configExceptionOf(
                        "$CLAIMS_KEY.${properties.id}.template",
                        "config.claim.template.cannot_reference_default"
                    )
                )
                return null
            }
            val template = templates[templateName]
            if (template == null) {
                ctx.addError(
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
}
