package com.sympauthy.config.properties

import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration of a claim template that defines default values for claims.
 *
 * The template named `default` is automatically applied when no explicit template is specified
 * on a claim. Custom templates can be referenced by name via the `template` property on a claim.
 */
@EachProperty(TEMPLATES_CLAIMS_KEY)
class ClaimTemplateConfigurationProperties(
    @param:Parameter val id: String
) {
    var enabled: String? = null
    var required: String? = null
    var group: String? = null
    var allowedValues: List<Any>? = null
    var audience: String? = null
    var acl: AclConfig? = null

    @ConfigurationProperties("acl")
    interface AclConfig : ClaimAclProperties

    companion object {
        const val TEMPLATES_CLAIMS_KEY = "templates.claims"

        const val DEFAULT = "default"
    }
}
