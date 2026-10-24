package com.sympauthy.config.model

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
    val acl: ClaimTemplateAcl
)

/**
 * ACL defaults from a claim template: the expression each door is opened by, as the file wrote it.
 *
 * All fields are nullable since a template only needs to define the defaults it wants to provide. An
 * expression is held unvalidated here and validated on each claim taking it, under the claim's own key.
 */
data class ClaimTemplateAcl(
    val collectedInFlow: String?,
    val readableByPerson: String?,
    val writableByPerson: String?,
    val readableByClient: String?,
    val writableByClient: String?
)
