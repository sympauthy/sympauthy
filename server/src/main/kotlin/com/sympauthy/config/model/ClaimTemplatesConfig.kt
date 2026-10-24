package com.sympauthy.config.model

import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
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
    val kind: ClaimKind?,
    val audienceId: String?,
    val allowedValues: List<Any>?,
    val publishedIn: Set<ClaimPublicationPlace>?,
    val publishedInWhenRequested: Set<ClaimPublicationPlace>?,
    /**
     * The doors this template opens, validated under its own keys, which a claim taking it inherits for
     * every door it writes nothing for.
     */
    val acl: ClaimAcl
)
