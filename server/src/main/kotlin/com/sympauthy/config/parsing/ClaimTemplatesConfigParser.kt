package com.sympauthy.config.parsing

import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

data class ParsedClaimTemplate(
    val id: String,
    val enabled: Boolean?,
    val required: Boolean?,
    val group: ClaimGroup?,
    val audienceId: String?,
    val allowedValues: List<Any>?,
    val acl: ParsedClaimAcl
)

@Singleton
class ClaimTemplatesConfigParser(
    @Inject private val parser: ConfigParser,
    @Inject private val claimAclParser: ClaimAclParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        templatesList: List<ClaimTemplateConfigurationProperties>
    ): List<ParsedClaimTemplate> {
        return templatesList.map { properties ->
            parseTemplate(ctx, properties)
        }
    }

    private fun parseTemplate(
        ctx: ConfigParsingContext,
        properties: ClaimTemplateConfigurationProperties
    ): ParsedClaimTemplate {
        val configKeyPrefix = "$TEMPLATES_CLAIMS_KEY.${properties.id}"

        val enabled = ctx.parse {
            parser.getBoolean(properties, "$configKeyPrefix.enabled", ClaimTemplateConfigurationProperties::enabled)
        }

        val required = ctx.parse {
            parser.getBoolean(properties, "$configKeyPrefix.required", ClaimTemplateConfigurationProperties::required)
        }

        val group = ctx.parse {
            properties.group?.let {
                parser.convertToEnum<ClaimGroup>("$configKeyPrefix.group", it)
            }
        }

        val acl = claimAclParser.parseTemplateAcl(ctx, properties.acl, configKeyPrefix)

        return ParsedClaimTemplate(
            id = properties.id,
            enabled = enabled,
            required = required,
            group = group,
            audienceId = properties.audience,
            allowedValues = properties.allowedValues,
            acl = acl
        )
    }
}
