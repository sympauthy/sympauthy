package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.properties.ClaimAclProperties
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Parsed ACL data shared between template ACL and full claim ACL.
 * All fields are nullable — null means the value was not set or failed to parse.
 */
data class ParsedClaimAcl(
    val consentScope: String?,
    val readableByPerson: Boolean?,
    val collectedInFlow: Boolean?,
    val writableByPerson: Boolean?,
    val readableByClient: Boolean?,
    val writableByClient: Boolean?,
    val readableWithClientScopes: List<String>?,
    val writableWithClientScopes: List<String>?,
    val writeMaxAuthenticationAge: Duration?
) {
    companion object {

        /** An ACL no file spoke for, which every field of falls back to whatever reads it. */
        val NONE = ParsedClaimAcl(null, null, null, null, null, null, null, null, null)
    }
}

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
            return ParsedClaimAcl.NONE
        }
        return ParsedClaimAcl(
            consentScope = acl.consentScope,
            readableByPerson = parseOptionalBoolean(
                ctx,
                acl.readableByPersonWhenConsented,
                "$configKeyPrefix.acl.readable-by-person-when-consented"
            ),
            collectedInFlow = parseOptionalBoolean(
                ctx,
                acl.collectedInFlowWhenConsented,
                "$configKeyPrefix.acl.collected-in-flow-when-consented"
            ),
            writableByPerson = parseOptionalBoolean(
                ctx,
                acl.writableByPersonWhenConsented,
                "$configKeyPrefix.acl.writable-by-person-when-consented"
            ),
            readableByClient = parseOptionalBoolean(
                ctx,
                acl.readableByClientWhenConsented,
                "$configKeyPrefix.acl.readable-by-client-when-consented"
            ),
            writableByClient = parseOptionalBoolean(
                ctx,
                acl.writableByClientWhenConsented,
                "$configKeyPrefix.acl.writable-by-client-when-consented"
            ),
            readableWithClientScopes = acl.readableWithClientScopesUnconditionally,
            writableWithClientScopes = acl.writableWithClientScopesUnconditionally,
            writeMaxAuthenticationAge = parseOptionalDuration(
                ctx,
                acl.writeMaxAuthenticationAge,
                "$configKeyPrefix.acl.write-max-authentication-age"
            )
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
            readableByPerson = resolveBoolean(
                ctx,
                acl?.readableByPersonWhenConsented,
                templateAcl?.readableByPersonWhenConsented,
                "$configKeyPrefix.acl.readable-by-person-when-consented"
            ),
            collectedInFlow = resolveBoolean(
                ctx,
                acl?.collectedInFlowWhenConsented,
                templateAcl?.collectedInFlowWhenConsented,
                "$configKeyPrefix.acl.collected-in-flow-when-consented"
            ),
            writableByPerson = resolveBoolean(
                ctx,
                acl?.writableByPersonWhenConsented,
                templateAcl?.writableByPersonWhenConsented,
                "$configKeyPrefix.acl.writable-by-person-when-consented"
            ),
            readableByClient = resolveBoolean(
                ctx,
                acl?.readableByClientWhenConsented,
                templateAcl?.readableByClientWhenConsented,
                "$configKeyPrefix.acl.readable-by-client-when-consented"
            ),
            writableByClient = resolveBoolean(
                ctx,
                acl?.writableByClientWhenConsented,
                templateAcl?.writableByClientWhenConsented,
                "$configKeyPrefix.acl.writable-by-client-when-consented"
            ),
            readableWithClientScopes = acl?.readableWithClientScopesUnconditionally
                ?: templateAcl?.readableWithClientScopesUnconditionally ?: emptyList(),
            writableWithClientScopes = acl?.writableWithClientScopesUnconditionally
                ?: templateAcl?.writableWithClientScopesUnconditionally ?: emptyList(),
            writeMaxAuthenticationAge = resolveDuration(
                ctx,
                acl?.writeMaxAuthenticationAge,
                templateAcl?.writeMaxAuthenticationAge,
                "$configKeyPrefix.acl.write-max-authentication-age"
            )
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

    private fun parseOptionalDuration(
        ctx: ConfigParsingContext,
        value: String?,
        configKey: String
    ): Duration? {
        if (value == null) return null
        return ctx.parse { parser.getDuration(value, configKey) { it } }
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

    private fun resolveDuration(
        ctx: ConfigParsingContext,
        propertyValue: String?,
        templateValue: Duration?,
        configKey: String
    ): Duration? {
        if (propertyValue != null) {
            return ctx.parse { parser.getDuration(propertyValue, configKey) { it } }
        }
        return templateValue
    }
}
