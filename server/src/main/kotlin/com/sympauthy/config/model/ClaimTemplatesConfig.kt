package com.sympauthy.config.model

import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.config.exception.ConfigurationException

sealed class ClaimTemplatesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledClaimTemplatesConfig(
    val templates: Map<String, ClaimTemplate>
) : ClaimTemplatesConfig()

class DisabledClaimTemplatesConfig(
    configurationErrors: List<ConfigurationException>
) : ClaimTemplatesConfig(configurationErrors)

fun ClaimTemplatesConfig.orThrow(): EnabledClaimTemplatesConfig {
    return when (this) {
        is EnabledClaimTemplatesConfig -> this
        is DisabledClaimTemplatesConfig -> throw this.invalidConfig
    }
}

fun ClaimTemplatesConfig.orNull(): EnabledClaimTemplatesConfig? {
    return this as? EnabledClaimTemplatesConfig
}

/**
 * A validated claim template holding default values for claim configurations.
 *
 * All properties are nullable since a template only needs to define the values it wants to provide as defaults.
 */
data class ClaimTemplate(
    val id: String,
    val enabled: Boolean?,
    val required: Boolean?,
    val group: ClaimGroup?,
    val audienceId: String?,
    val allowedValues: List<Any>?,
    val acl: ClaimTemplateAcl
)

/**
 * Parsed ACL defaults from a claim template.
 *
 * All fields are nullable since a template only needs to define the defaults it wants to provide.
 */
data class ClaimTemplateAcl(
    val consentScope: String?,
    val readableByUserWhenConsented: Boolean?,
    val writableByUserWhenConsented: Boolean?,
    val readableByClientWhenConsented: Boolean?,
    val writableByClientWhenConsented: Boolean?,
    val readableWithClientScopesUnconditionally: List<String>?,
    val writableWithClientScopesUnconditionally: List<String>?
)
