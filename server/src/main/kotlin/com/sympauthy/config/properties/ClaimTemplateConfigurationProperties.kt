package com.sympauthy.config.properties

import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration of a claim template that defines default values for the claims naming it;
 * `docs/design/claims.md` says how a claim takes one.
 */
@EachProperty(TEMPLATES_CLAIMS_KEY)
class ClaimTemplateConfigurationProperties(
    @param:Parameter val id: String
) {
    var enabled: String? = null
    var required: String? = null
    var group: String? = null
    var allowedValues: List<Any>? = null
    var maxSize: String? = null
    var kind: String? = null
    var audience: String? = null
    var publishedIn: List<String>? = null
    var publishedInWhenRequested: List<String>? = null
    var acl: AclConfig? = null

    @ConfigurationProperties("acl")
    interface AclConfig : ClaimAclProperties

    companion object {
        const val TEMPLATES_CLAIMS_KEY = "templates.claims"
    }
}
