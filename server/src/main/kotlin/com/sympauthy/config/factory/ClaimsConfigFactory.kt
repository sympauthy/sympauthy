package com.sympauthy.config.factory

import com.sympauthy.business.model.oauth2.isBuiltInClientScope
import com.sympauthy.business.model.oauth2.isBuiltInGrantableScope
import com.sympauthy.business.model.oauth2.isAdminScope
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.business.model.user.isOpenIdConnectScope
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
    @Inject private val scopesConfig: ScopesConfig
) {

    @Singleton
    fun provideClaims(
        propertiesList: List<ClaimConfigurationProperties>
    ): ClaimsConfig {
        val enabledTemplatesConfig = claimTemplatesConfig.orNull()
            ?: return DisabledClaimsConfig(emptyList())
        val templates = enabledTemplatesConfig.templates

        val enabledScopesConfig = scopesConfig.orNull()
        val knownScopeIds = collectKnownScopeIds(enabledScopesConfig)

        val errors = mutableListOf<ConfigurationException>()

        val openIdClaims = OpenIdClaim.entries.map { openIdClaim ->
            if (openIdClaim.generated) {
                provideGeneratedClaim(
                    properties = propertiesList.firstOrNull { it.id.normalizeClaimId() == openIdClaim.id },
                    openIdClaim = openIdClaim,
                    templates = templates,
                    knownScopeIds = knownScopeIds,
                    errors = errors
                )
            } else {
                provideOpenIdClaim(
                    properties = propertiesList.firstOrNull { it.id.normalizeClaimId() == openIdClaim.id },
                    openIdClaim = openIdClaim,
                    templates = templates,
                    knownScopeIds = knownScopeIds,
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
                    knownScopeIds = knownScopeIds,
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
     * Collect all known scope IDs from built-in scopes and the scopes configuration.
     */
    private fun collectKnownScopeIds(enabledScopesConfig: EnabledScopesConfig?): Set<String> {
        val ids = mutableSetOf<String>()
        com.sympauthy.business.model.user.OpenIdConnectScope.entries.forEach { ids.add(it.scope) }
        com.sympauthy.business.model.oauth2.BuiltInGrantableScope.entries.forEach { ids.add(it.scope) }
        com.sympauthy.business.model.oauth2.AdminScope.entries.forEach { ids.add(it.scope) }
        com.sympauthy.business.model.oauth2.BuiltInClientScope.entries.forEach { ids.add(it.scope) }
        enabledScopesConfig?.scopes?.forEach { ids.add(it.scope) }
        return ids
    }

    // region Scope resolution utilities

    /**
     * Resolve a single scope ID from properties, falling back to the template, then to [defaultScope].
     * Validates that the resolved scope exists in [knownScopeIds] when non-null.
     */
    private fun resolveConsentScope(
        propertyValue: String?,
        templateValue: String?,
        defaultScope: String?,
        configKey: String,
        knownScopeIds: Set<String>,
        errors: MutableList<ConfigurationException>
    ): String? {
        val scope = propertyValue ?: templateValue ?: defaultScope
        if (scope != null && scope !in knownScopeIds) {
            errors.add(
                configExceptionOf(
                    configKey,
                    "config.claim.acl.unknown_scope",
                    "scope" to scope
                )
            )
        }
        return scope
    }

    /**
     * Resolve a list of scope IDs from properties, falling back to the template, then to an empty list.
     * Validates that each scope exists in [knownScopeIds].
     */
    private fun resolveScopeList(
        propertyValue: List<String>?,
        templateValue: List<String>?,
        configKey: String,
        knownScopeIds: Set<String>,
        errors: MutableList<ConfigurationException>
    ): List<String> {
        val scopes = propertyValue ?: templateValue ?: emptyList()
        scopes.forEachIndexed { index, scope ->
            if (scope.isBlank()) {
                errors.add(configExceptionOf("${configKey}[$index]", "config.empty"))
            } else if (scope !in knownScopeIds) {
                errors.add(
                    configExceptionOf(
                        "${configKey}[$index]",
                        "config.claim.acl.unknown_scope",
                        "scope" to scope
                    )
                )
            }
        }
        return scopes
    }

    // endregion

    /**
     * Generated claims (e.g. `sub`, `updated_at`) have server-managed values.
     * They are always enabled, read-only, and gated by their OpenID scope.
     * Only [UnconditionalAcl.readableWithClientScopes] is configurable via YAML/templates.
     */
    private fun provideGeneratedClaim(
        properties: ClaimConfigurationProperties?,
        openIdClaim: OpenIdClaim,
        templates: Map<String, ClaimTemplate>,
        knownScopeIds: Set<String>,
        errors: MutableList<ConfigurationException>
    ): Claim {
        val template = if (properties != null) {
            resolveTemplate(properties, templates, errors)
        } else {
            templates[DEFAULT]
        }
        val configKeyPrefix = "$CLAIMS_KEY.${openIdClaim.id}"

        val readableWithClientScopes = resolveScopeList(
            propertyValue = properties?.acl?.readableWithClientScopesUnconditionally,
            templateValue = template?.readableWithClientScopesUnconditionally,
            configKey = "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally",
            knownScopeIds = knownScopeIds,
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
            acl = ClaimAcl(
                consent = ConsentAcl(
                    scope = openIdClaim.scope.scope,
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

    private fun buildAcl(
        properties: ClaimConfigurationProperties?,
        template: ClaimTemplate?,
        configKeyPrefix: String,
        defaultConsentScope: String?,
        knownScopeIds: Set<String>,
        errors: MutableList<ConfigurationException>
    ): ClaimAcl {
        val acl = properties?.acl

        val consentScope = resolveConsentScope(
            propertyValue = acl?.scopeWhenConsented,
            templateValue = template?.consentScope,
            defaultScope = defaultConsentScope,
            configKey = "$configKeyPrefix.acl.scope-when-consented",
            knownScopeIds = knownScopeIds,
            errors = errors
        )

        val readableByUser = try {
            acl?.readableByUserWhenConsented?.let {
                parser.getBoolean(properties, "$configKeyPrefix.acl.readable-by-user-when-consented") { it }
            } ?: template?.readableByUserWhenConsented ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            false
        }

        val writableByUser = try {
            acl?.writableByUserWhenConsented?.let {
                parser.getBoolean(properties, "$configKeyPrefix.acl.writable-by-user-when-consented") { it }
            } ?: template?.writableByUserWhenConsented ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            false
        }

        val readableByClient = try {
            acl?.readableByClientWhenConsented?.let {
                parser.getBoolean(properties, "$configKeyPrefix.acl.readable-by-client-when-consented") { it }
            } ?: template?.readableByClientWhenConsented ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            false
        }

        val writableByClient = try {
            acl?.writableByClientWhenConsented?.let {
                parser.getBoolean(properties, "$configKeyPrefix.acl.writable-by-client-when-consented") { it }
            } ?: template?.writableByClientWhenConsented ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            false
        }

        val readableWithClientScopes = resolveScopeList(
            propertyValue = acl?.readableWithClientScopesUnconditionally,
            templateValue = template?.readableWithClientScopesUnconditionally,
            configKey = "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally",
            knownScopeIds = knownScopeIds,
            errors = errors
        )

        val writableWithClientScopes = resolveScopeList(
            propertyValue = acl?.writableWithClientScopesUnconditionally,
            templateValue = template?.writableWithClientScopesUnconditionally,
            configKey = "$configKeyPrefix.acl.writable-with-client-scopes-unconditionally",
            knownScopeIds = knownScopeIds,
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

    private fun provideOpenIdClaim(
        properties: ClaimConfigurationProperties?,
        openIdClaim: OpenIdClaim,
        templates: Map<String, ClaimTemplate>,
        knownScopeIds: Set<String>,
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

        val acl = buildAcl(
            properties = properties,
            template = template,
            configKeyPrefix = configKeyPrefix,
            defaultConsentScope = openIdClaim.scope.scope,
            knownScopeIds = knownScopeIds,
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
        knownScopeIds: Set<String>,
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

        val acl = buildAcl(
            properties = properties,
            template = template,
            configKeyPrefix = configKeyPrefix,
            defaultConsentScope = null,
            knownScopeIds = knownScopeIds,
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
