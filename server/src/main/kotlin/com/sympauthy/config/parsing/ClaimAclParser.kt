package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.properties.ClaimAclProperties
import jakarta.inject.Singleton

/**
 * Parsed ACL data shared between template ACL and full claim ACL.
 * All fields are nullable — null means the value was not set or failed to parse.
 */
data class ParsedClaimAcl(
    val consentScope: String?,
    val readableByUser: Boolean?,
    val writableByUser: Boolean?,
    val readableByClient: Boolean?,
    val writableByClient: Boolean?,
    val readableWithClientScopes: List<String>?,
    val writableWithClientScopes: List<String>?
)

/**
 * Parses ACL properties into typed values.
 * Handles boolean type conversion and template fallback resolution.
 * Does not validate scope references — that is done by [com.sympauthy.config.validation.ClaimAclValidator].
 */
@Singleton
class ClaimAclParser(
    private val parser: ConfigParser
) {

    /**
     * Parse a template ACL. All fields are nullable (no defaults applied).
     */
    fun parseTemplateAcl(
        ctx: ConfigParsingContext,
        acl: ClaimAclProperties?,
        configKeyPrefix: String
    ): ParsedClaimAcl {
        if (acl == null) {
            return ParsedClaimAcl(null, null, null, null, null, null, null)
        }
        return ParsedClaimAcl(
            consentScope = acl.consentScope,
            readableByUser = parseOptionalBoolean(ctx, acl.readableByUserWhenConsented, "$configKeyPrefix.acl.readable-by-user-when-consented"),
            writableByUser = parseOptionalBoolean(ctx, acl.writableByUserWhenConsented, "$configKeyPrefix.acl.writable-by-user-when-consented"),
            readableByClient = parseOptionalBoolean(ctx, acl.readableByClientWhenConsented, "$configKeyPrefix.acl.readable-by-client-when-consented"),
            writableByClient = parseOptionalBoolean(ctx, acl.writableByClientWhenConsented, "$configKeyPrefix.acl.writable-by-client-when-consented"),
            readableWithClientScopes = acl.readableWithClientScopesUnconditionally,
            writableWithClientScopes = acl.writableWithClientScopesUnconditionally
        )
    }

    /**
     * Parse a full claim ACL with template fallback and defaults.
     * Booleans fall back to the template value, then to `false`.
     * Scope lists fall back to the template value, then to empty list.
     */
    fun parseAcl(
        ctx: ConfigParsingContext,
        acl: ClaimAclProperties?,
        template: ClaimTemplate?,
        configKeyPrefix: String,
        defaultConsentScope: String?
    ): ParsedClaimAcl {
        val templateAcl = template?.acl
        return ParsedClaimAcl(
            consentScope = acl?.consentScope ?: templateAcl?.consentScope ?: defaultConsentScope,
            readableByUser = resolveBoolean(ctx, acl?.readableByUserWhenConsented, templateAcl?.readableByUserWhenConsented, "$configKeyPrefix.acl.readable-by-user-when-consented"),
            writableByUser = resolveBoolean(ctx, acl?.writableByUserWhenConsented, templateAcl?.writableByUserWhenConsented, "$configKeyPrefix.acl.writable-by-user-when-consented"),
            readableByClient = resolveBoolean(ctx, acl?.readableByClientWhenConsented, templateAcl?.readableByClientWhenConsented, "$configKeyPrefix.acl.readable-by-client-when-consented"),
            writableByClient = resolveBoolean(ctx, acl?.writableByClientWhenConsented, templateAcl?.writableByClientWhenConsented, "$configKeyPrefix.acl.writable-by-client-when-consented"),
            readableWithClientScopes = acl?.readableWithClientScopesUnconditionally ?: templateAcl?.readableWithClientScopesUnconditionally ?: emptyList(),
            writableWithClientScopes = acl?.writableWithClientScopesUnconditionally ?: templateAcl?.writableWithClientScopesUnconditionally ?: emptyList()
        )
    }

    /**
     * Parse a generated claim ACL. Only [readableWithClientScopes] is configurable.
     */
    fun parseGeneratedClaimAcl(
        ctx: ConfigParsingContext,
        acl: ClaimAclProperties?,
        template: ClaimTemplate?,
        configKeyPrefix: String
    ): ParsedClaimAcl {
        val readableWithClientScopes = acl?.readableWithClientScopesUnconditionally
            ?: template?.acl?.readableWithClientScopesUnconditionally
            ?: emptyList()
        return ParsedClaimAcl(
            consentScope = null,
            readableByUser = null,
            writableByUser = null,
            readableByClient = null,
            writableByClient = null,
            readableWithClientScopes = readableWithClientScopes,
            writableWithClientScopes = emptyList()
        )
    }

    private fun parseOptionalBoolean(
        ctx: ConfigParsingContext,
        value: String?,
        configKey: String
    ): Boolean? {
        if (value == null) return null
        return ctx.parse { parser.getBoolean(value, configKey) { it } }
    }

    private fun resolveBoolean(
        ctx: ConfigParsingContext,
        propertyValue: String?,
        templateValue: Boolean?,
        configKey: String
    ): Boolean {
        if (propertyValue != null) {
            return ctx.parse { parser.getBoolean(propertyValue, configKey) { it } } ?: false
        }
        return templateValue ?: false
    }
}
