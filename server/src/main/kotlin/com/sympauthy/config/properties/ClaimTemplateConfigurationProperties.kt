package com.sympauthy.config.properties

import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration of a claim template that defines default values for claims.
 *
 * A claim names the one it takes through its `template` property, and no template is applied to a
 * claim naming none. The server ships `personal` and `application`, one per claim kind.
 */
@EachProperty(TEMPLATES_CLAIMS_KEY)
class ClaimTemplateConfigurationProperties(
    @param:Parameter val id: String
) {
    var enabled: String? = null
    var required: String? = null
    var group: String? = null
    var allowedValues: List<Any>? = null
    var kind: String? = null
    var audience: String? = null
    var publishedIn: List<String>? = null
    var acl: AclConfig? = null

    @ConfigurationProperties("acl")
    interface AclConfig : ClaimAclProperties

    companion object {
        const val TEMPLATES_CLAIMS_KEY = "templates.claims"
    }
}
