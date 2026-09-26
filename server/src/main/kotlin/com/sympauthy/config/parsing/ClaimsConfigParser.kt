package com.sympauthy.config.parsing

import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.*
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimPublication
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.DEFAULT
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Micronaut normalizes property keys to kebab-case (e.g. `preferred_username` becomes `preferred-username`).
 * OpenID claim IDs use underscores. This function normalizes the Micronaut key back to match the OpenID ID.
 */
internal fun String.normalizeClaimId() = replace('-', '_')

/**
 * Convert each of the [values] into the OpenID channel it names, recording an error against the entry's own
 * index for every one that names none, so a file naming two unknown channels reports both. [key] is the
 * property the entries were written under, whether that is a claim's or a template's.
 *
 * Returns null where [values] is null — nothing written, which falls through to whatever a template offers.
 * An entry-less list is a claim naming no channel, which does not.
 */
internal fun parsePublishedIn(
    ctx: ConfigParsingContext,
    parser: ConfigParser,
    values: List<String>?,
    key: String
): Set<ClaimPublication>? = values
    ?.mapIndexedNotNull { index, value ->
        ctx.parse { parser.convertToEnum<ClaimPublication>("$key[$index]", value) }
    }
    ?.toSet()

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
    val publishedIn: Set<ClaimPublication>,
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
        val generatedClaims = GeneratedOpenIdConnectClaim.entries.map(::parseGeneratedClaim)

        // A template several claims name is converted once per type rather than once per claim, so that a
        // mistake in one is reported once instead of once for every claim that inherits it.
        val inheritedAllowedValues = mutableMapOf<Pair<String, ClaimDataType>, List<Any>?>()

        val configurableClaims = propertiesList.mapNotNull { properties ->
            val normalizedId = properties.id.normalizeClaimId()
            if (normalizedId in GeneratedOpenIdConnectClaim.ids) return@mapNotNull null
            parseClaim(ctx, properties, templates, inheritedAllowedValues)
        }

        return generatedClaims + configurableClaims
    }

    /**
     * The claim [generatedClaim] describes, which a deployment's file has no part in.
     *
     * Nothing is read off `claims.<id>` here, not even the template it might name: every key that
     * section accepts is refused by [com.sympauthy.config.validation.ClaimsConfigValidator], so there
     * is nothing to resolve and nothing to fall back to.
     */
    private fun parseGeneratedClaim(generatedClaim: GeneratedOpenIdConnectClaim): ParsedClaim {
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
            // The channels the claim already reaches, which is a fact about this server rather than a
            // decision a file takes: neither channel reads a generated claim out of the collected claims,
            // so no `published-in` written here could move one. It is the truth rather than the withholding
            // value because the discovery document reads it to say what a channel can supply.
            publishedIn = generatedClaim.publishedIn,
            acl = ParsedClaimAcl.NONE
        )
    }

    private fun parseClaim(
        ctx: ConfigParsingContext,
        properties: ClaimConfigurationProperties,
        templates: Map<String, ClaimTemplate>,
        inheritedAllowedValues: MutableMap<Pair<String, ClaimDataType>, List<Any>?>
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
            inheritedAllowedValues.getOrPut(template.id to dataType) {
                parseAllowedValues(
                    ctx, template.allowedValues, "$TEMPLATES_CLAIMS_KEY.${template.id}.allowed-values", dataType
                )
            }
        } else null

        // No channel, for a claim and for a template that names none: a value leaves this server where a
        // deployment said so and nowhere else. The shipped `openid` template names both, so the claims the
        // specification defines keep reaching both without every file having to say it again.
        val publishedIn = parsePublishedIn(ctx, parser, properties.publishedIn, "$configKeyPrefix.published-in")
            ?: template?.publishedIn
            ?: emptySet()

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
            publishedIn = publishedIn,
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
     * can convert for one type and fail for another.
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
                when (type) {
                    BOOLEAN -> parser.getBoolean(value, itemKey) { it }
                    NUMBER -> parser.getLong(value, itemKey) { it }
                    DATE, EMAIL, PHONE_NUMBER, STRING, TIMEZONE ->
                        parser.getString(value, itemKey) { it }
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
