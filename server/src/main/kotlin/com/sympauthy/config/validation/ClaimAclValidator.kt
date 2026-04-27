package com.sympauthy.config.validation

import com.sympauthy.business.model.oauth2.BuiltInClientScope
import com.sympauthy.business.model.user.OpenIdConnectScope
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.model.CustomScopeConfig
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.orNull
import com.sympauthy.config.parsing.ParsedClaimAcl
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Validates scope references in parsed ACL data and builds final ACL models.
 */
@Singleton
class ClaimAclValidator(
    @Inject private val scopesConfig: ScopesConfig
) {

    private val consentableScopeIds: Set<String> by lazy {
        val ids = mutableSetOf<String>()
        OpenIdConnectScope.entries.forEach { ids.add(it.scope) }
        scopesConfig.orNull()?.scopes
            ?.filterIsInstance<CustomScopeConfig>()
            ?.filter { it.consentable }
            ?.forEach { ids.add(it.scope) }
        ids
    }

    private val clientScopeIds: Set<String> by lazy {
        BuiltInClientScope.entries.map { it.scope }.toSet()
    }

    /**
     * Validate a template ACL. Checks that referenced scopes exist.
     */
    fun validateTemplateAcl(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimAcl,
        configKeyPrefix: String
    ): ClaimTemplateAcl {
        validateConsentScope(ctx, parsed.consentScope, "$configKeyPrefix.acl.consent-scope")
        validateClientScopeList(ctx, parsed.readableWithClientScopes, "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally")
        validateClientScopeList(ctx, parsed.writableWithClientScopes, "$configKeyPrefix.acl.writable-with-client-scopes-unconditionally")

        return ClaimTemplateAcl(
            consentScope = parsed.consentScope,
            readableByUserWhenConsented = parsed.readableByUser,
            writableByUserWhenConsented = parsed.writableByUser,
            readableByClientWhenConsented = parsed.readableByClient,
            writableByClientWhenConsented = parsed.writableByClient,
            readableWithClientScopesUnconditionally = parsed.readableWithClientScopes,
            writableWithClientScopesUnconditionally = parsed.writableWithClientScopes
        )
    }

    /**
     * Validate a full claim ACL. Checks scope references and builds the final [ClaimAcl].
     */
    fun validateAcl(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimAcl,
        configKeyPrefix: String
    ): ClaimAcl {
        validateConsentScope(ctx, parsed.consentScope, "$configKeyPrefix.acl.consent-scope")
        validateClientScopeList(ctx, parsed.readableWithClientScopes, "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally")
        validateClientScopeList(ctx, parsed.writableWithClientScopes, "$configKeyPrefix.acl.writable-with-client-scopes-unconditionally")

        return ClaimAcl(
            consent = ConsentAcl(
                scope = parsed.consentScope,
                readableByUser = parsed.readableByUser ?: false,
                writableByUser = parsed.writableByUser ?: false,
                readableByClient = parsed.readableByClient ?: false,
                writableByClient = parsed.writableByClient ?: false
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = parsed.readableWithClientScopes ?: emptyList(),
                writableWithClientScopes = parsed.writableWithClientScopes ?: emptyList()
            )
        )
    }

    /**
     * Validate a generated claim ACL. Only validates [readableWithClientScopes].
     */
    fun validateGeneratedClaimAcl(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimAcl,
        configKeyPrefix: String,
        consentScope: String
    ): ClaimAcl {
        validateClientScopeList(ctx, parsed.readableWithClientScopes, "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally")

        return ClaimAcl(
            consent = ConsentAcl(
                scope = consentScope,
                readableByUser = true,
                writableByUser = false,
                readableByClient = true,
                writableByClient = false
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = parsed.readableWithClientScopes ?: emptyList(),
                writableWithClientScopes = emptyList()
            )
        )
    }

    private fun validateConsentScope(ctx: ConfigParsingContext, scope: String?, configKey: String) {
        if (scope != null && scope !in consentableScopeIds) {
            ctx.addError(
                configExceptionOf(configKey, "config.claim.acl.not_consentable_scope", "scope" to scope)
            )
        }
    }

    private fun validateClientScopeList(ctx: ConfigParsingContext, scopes: List<String>?, configKey: String) {
        scopes?.forEachIndexed { index, scope ->
            if (scope.isBlank()) {
                ctx.addError(configExceptionOf("${configKey}[$index]", "config.empty"))
            } else if (scope !in clientScopeIds) {
                ctx.addError(
                    configExceptionOf("${configKey}[$index]", "config.claim.acl.not_client_scope", "scope" to scope)
                )
            }
        }
    }
}
