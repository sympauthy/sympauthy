package com.sympauthy.config.parsing

import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.DEFAULT
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import com.sympauthy.config.util.configName
import jakarta.inject.Inject
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
    val acl: ParsedClaimAcl
)

@Singleton
class ClaimsConfigParser(
    @Inject private val parser: ConfigParser,
    @Inject private val claimAclParser: ClaimAclParser
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
        val configKeyPrefix = "$CLAIMS_KEY.${generatedClaim.id}"
        val acl = claimAclParser.parseGeneratedClaimAcl(ctx, properties?.acl, template, configKeyPrefix)

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
            acl = acl
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

        val declaredAllowedValues = properties.allowedValues
        val allowedValues = if (declaredAllowedValues != null) {
            parseAllowedValues(ctx, declaredAllowedValues, "$configKeyPrefix.allowed-values", dataType)
        } else if (template != null) {
            parseAllowedValues(
                ctx, template.allowedValues, "$TEMPLATES_CLAIMS_KEY.${template.id}.allowed-values", dataType
            )
        } else null

        val acl = claimAclParser.parseAcl(ctx, properties.acl, template, configKeyPrefix, null)

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
            acl = acl
        )
    }

    /**
     * Convert each of the [values] into the primitive the claim's [type] is exchanged as, recording an error
     * against [key] for every entry that cannot be. Returns null when [values] is null, and otherwise the
     * entries that converted.
     *
     * [values] may be the ones a claim declares or the ones it inherits from a template, and [key] names
     * whichever of the two they are written under. A template carries no type of its own, so its entries can
     * only be converted once a claim naming the template supplies one — which also means the same template
     * can convert for one claim and fail for another.
     */
    internal fun parseAllowedValues(
        ctx: ConfigParsingContext,
        values: List<Any>?,
        key: String,
        type: ClaimDataType
    ): List<Any>? {
        return values?.mapIndexedNotNull { index, value ->
            val itemKey = "$key[$index]"
            ctx.parse {
                when (type.typeClass) {
                    String::class -> parser.getString(value, itemKey) { it }
                    Long::class -> parser.getLong(value, itemKey) { it }
                    else -> throw configExceptionOf(
                        itemKey, "config.claim.allowed_values.unsupported_type",
                        "type" to type.configName
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
