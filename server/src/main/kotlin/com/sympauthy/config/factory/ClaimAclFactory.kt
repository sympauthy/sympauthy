package com.sympauthy.config.factory

import com.sympauthy.business.model.oauth2.BuiltInClientScope
import com.sympauthy.business.model.user.OpenIdConnectScope
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.model.CustomScopeConfig
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.orNull
import com.sympauthy.config.properties.ClaimAclProperties
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Builds [ClaimAcl] instances from claim configuration properties and templates.
 *
 * Handles template fallback, boolean parsing with error accumulation,
 * and validates that all referenced scopes exist.
 */
@Singleton
class ClaimAclFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val scopesConfig: ScopesConfig
) {

    /**
     * Consentable scope IDs: OpenID Connect scopes + custom consentable scopes.
     * Used to validate [ConsentAcl.scope].
     */
    private val consentableScopeIds: Set<String> by lazy {
        val ids = mutableSetOf<String>()
        OpenIdConnectScope.entries.forEach { ids.add(it.scope) }
        scopesConfig.orNull()?.scopes
            ?.filterIsInstance<CustomScopeConfig>()
            ?.filter { it.consentable }
            ?.forEach { ids.add(it.scope) }
        ids
    }

    /**
     * Client scope IDs: built-in client scopes.
     * Used to validate [UnconditionalAcl.readableWithClientScopes] and [UnconditionalAcl.writableWithClientScopes].
     */
    private val clientScopeIds: Set<String> by lazy {
        BuiltInClientScope.entries.map { it.scope }.toSet()
    }

    /**
     * Build a full [ClaimAcl] from claim properties and template, with fallback to [defaultConsentScope].
     */
    /**
     * Build a fully resolved [ClaimAcl] from claim ACL properties and template defaults,
     * falling back to [defaultConsentScope] for the consent scope.
     */
    fun buildAcl(
        acl: ClaimAclProperties?,
        template: ClaimTemplate?,
        configKeyPrefix: String,
        defaultConsentScope: String?,
        errors: MutableList<ConfigurationException>
    ): ClaimAcl {
        val templateAcl = template?.acl

        val consentScope = resolveConsentScope(
            propertyValue = acl?.consentScope,
            templateValue = templateAcl?.consentScope,
            defaultScope = defaultConsentScope,
            configKey = "$configKeyPrefix.acl.consent-scope",
            errors = errors
        )

        val readableByUser = resolveBoolean(
            propertyValue = acl?.readableByUserWhenConsented,
            templateValue = templateAcl?.readableByUserWhenConsented,
            configKey = "$configKeyPrefix.acl.readable-by-user-when-consented",
            errors = errors
        )

        val writableByUser = resolveBoolean(
            propertyValue = acl?.writableByUserWhenConsented,
            templateValue = templateAcl?.writableByUserWhenConsented,
            configKey = "$configKeyPrefix.acl.writable-by-user-when-consented",
            errors = errors
        )

        val readableByClient = resolveBoolean(
            propertyValue = acl?.readableByClientWhenConsented,
            templateValue = templateAcl?.readableByClientWhenConsented,
            configKey = "$configKeyPrefix.acl.readable-by-client-when-consented",
            errors = errors
        )

        val writableByClient = resolveBoolean(
            propertyValue = acl?.writableByClientWhenConsented,
            templateValue = templateAcl?.writableByClientWhenConsented,
            configKey = "$configKeyPrefix.acl.writable-by-client-when-consented",
            errors = errors
        )

        val readableWithClientScopes = resolveScopeList(
            propertyValue = acl?.readableWithClientScopesUnconditionally,
            templateValue = templateAcl?.readableWithClientScopesUnconditionally,
            configKey = "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally",
            errors = errors
        )

        val writableWithClientScopes = resolveScopeList(
            propertyValue = acl?.writableWithClientScopesUnconditionally,
            templateValue = templateAcl?.writableWithClientScopesUnconditionally,
            configKey = "$configKeyPrefix.acl.writable-with-client-scopes-unconditionally",
            errors = errors
        )

        return ClaimAcl(
            consent = ConsentAcl(
                scope = consentScope,
                readableByUser = readableByUser,
                writableByUser = writableByUser,
                readableByClient = readableByClient,
                writableByClient = writableByClient
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = readableWithClientScopes,
                writableWithClientScopes = writableWithClientScopes
            )
        )
    }

    /**
     * Build a read-only [ClaimAcl] for generated claims (e.g. `sub`, `updated_at`).
     * Only [UnconditionalAcl.readableWithClientScopes] is configurable.
     */
    fun buildGeneratedClaimAcl(
        acl: ClaimAclProperties?,
        template: ClaimTemplate?,
        configKeyPrefix: String,
        consentScope: String,
        errors: MutableList<ConfigurationException>
    ): ClaimAcl {
        val readableWithClientScopes = resolveScopeList(
            propertyValue = acl?.readableWithClientScopesUnconditionally,
            templateValue = template?.acl?.readableWithClientScopesUnconditionally,
            configKey = "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally",
            errors = errors
        )

        return ClaimAcl(
            consent = ConsentAcl(
                scope = consentScope,
                readableByUser = true,
                writableByUser = false,
                readableByClient = true,
                writableByClient = false
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = readableWithClientScopes,
                writableWithClientScopes = emptyList()
            )
        )
    }

    /**
     * Build a [ClaimTemplateAcl] from ACL properties with all nullable fields.
     * Used by [ClaimTemplatesConfigFactory] to build template defaults.
     * Validates scope references and boolean values, accumulating errors.
     */
    fun buildTemplateAcl(
        acl: ClaimAclProperties?,
        configKeyPrefix: String,
        errors: MutableList<ConfigurationException>
    ): ClaimTemplateAcl {
        if (acl == null) {
            return ClaimTemplateAcl(
                consentScope = null,
                readableByUserWhenConsented = null,
                writableByUserWhenConsented = null,
                readableByClientWhenConsented = null,
                writableByClientWhenConsented = null,
                readableWithClientScopesUnconditionally = null,
                writableWithClientScopesUnconditionally = null
            )
        }

        val consentScope = acl.consentScope?.also { scope ->
            if (scope !in consentableScopeIds) {
                errors.add(
                    configExceptionOf(
                        "$configKeyPrefix.acl.consent-scope",
                        "config.claim.acl.not_consentable_scope",
                        "scope" to scope
                    )
                )
            }
        }

        val readableByUser = parseOptionalBoolean(
            acl.readableByUserWhenConsented,
            "$configKeyPrefix.acl.readable-by-user-when-consented",
            errors
        )

        val writableByUser = parseOptionalBoolean(
            acl.writableByUserWhenConsented,
            "$configKeyPrefix.acl.writable-by-user-when-consented",
            errors
        )

        val readableByClient = parseOptionalBoolean(
            acl.readableByClientWhenConsented,
            "$configKeyPrefix.acl.readable-by-client-when-consented",
            errors
        )

        val writableByClient = parseOptionalBoolean(
            acl.writableByClientWhenConsented,
            "$configKeyPrefix.acl.writable-by-client-when-consented",
            errors
        )

        val readableWithClientScopes = acl.readableWithClientScopesUnconditionally?.also { scopes ->
            validateScopeList(scopes, "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally", errors)
        }

        val writableWithClientScopes = acl.writableWithClientScopesUnconditionally?.also { scopes ->
            validateScopeList(scopes, "$configKeyPrefix.acl.writable-with-client-scopes-unconditionally", errors)
        }

        return ClaimTemplateAcl(
            consentScope = consentScope,
            readableByUserWhenConsented = readableByUser,
            writableByUserWhenConsented = writableByUser,
            readableByClientWhenConsented = readableByClient,
            writableByClientWhenConsented = writableByClient,
            readableWithClientScopesUnconditionally = readableWithClientScopes,
            writableWithClientScopesUnconditionally = writableWithClientScopes
        )
    }

    /**
     * Parse an optional boolean string, returning null if the input is null.
     */
    private fun parseOptionalBoolean(
        value: String?,
        configKey: String,
        errors: MutableList<ConfigurationException>
    ): Boolean? {
        return try {
            value?.let { parser.getBoolean(it, configKey) { it } }
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }
    }

    /**
     * Validate each scope in a list is a known client scope.
     */
    private fun validateScopeList(
        scopes: List<String>,
        configKey: String,
        errors: MutableList<ConfigurationException>
    ) {
        scopes.forEachIndexed { index, scope ->
            if (scope.isBlank()) {
                errors.add(configExceptionOf("${configKey}[$index]", "config.empty"))
            } else if (scope !in clientScopeIds) {
                errors.add(
                    configExceptionOf(
                        "${configKey}[$index]",
                        "config.claim.acl.not_client_scope",
                        "scope" to scope
                    )
                )
            }
        }
    }

    /**
     * Resolve a boolean ACL flag from properties, falling back to the template, then to `false`.
     */
    private fun resolveBoolean(
        propertyValue: String?,
        templateValue: Boolean?,
        configKey: String,
        errors: MutableList<ConfigurationException>
    ): Boolean {
        return try {
            propertyValue?.let {
                parser.getBoolean(it, configKey) { it }
            } ?: templateValue ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            false
        }
    }

    /**
     * Resolve a single consent scope ID from properties, falling back to the template, then to [defaultScope].
     * Validates that the resolved scope is a known consentable scope.
     */
    private fun resolveConsentScope(
        propertyValue: String?,
        templateValue: String?,
        defaultScope: String?,
        configKey: String,
        errors: MutableList<ConfigurationException>
    ): String? {
        val scope = propertyValue ?: templateValue ?: defaultScope
        if (scope != null && scope !in consentableScopeIds) {
            errors.add(
                configExceptionOf(
                    configKey,
                    "config.claim.acl.not_consentable_scope",
                    "scope" to scope
                )
            )
        }
        return scope
    }

    /**
     * Resolve a list of client scope IDs from properties, falling back to the template, then to an empty list.
     * Validates that each scope is a known client scope.
     */
    private fun resolveScopeList(
        propertyValue: List<String>?,
        templateValue: List<String>?,
        configKey: String,
        errors: MutableList<ConfigurationException>
    ): List<String> {
        val scopes = propertyValue ?: templateValue ?: emptyList()
        scopes.forEachIndexed { index, scope ->
            if (scope.isBlank()) {
                errors.add(configExceptionOf("${configKey}[$index]", "config.empty"))
            } else if (scope !in clientScopeIds) {
                errors.add(
                    configExceptionOf(
                        "${configKey}[$index]",
                        "config.claim.acl.not_client_scope",
                        "scope" to scope
                    )
                )
            }
        }
        return scopes
    }
}
