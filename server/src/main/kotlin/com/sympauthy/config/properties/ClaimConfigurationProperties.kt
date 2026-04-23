package com.sympauthy.config.properties

import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

@EachProperty(CLAIMS_KEY)
class ClaimConfigurationProperties(
    @param:Parameter val id: String
) {
    var template: String? = null
    var enabled: String? = null
    var required: String? = null
    var type: String? = null
    var group: String? = null
    var verifiedId: String? = null
    var allowedValues: List<Any>? = null
    var audience: String? = null
    var acl: AclConfig? = null

    @ConfigurationProperties("acl")
    interface AclConfig : ClaimAclProperties

    companion object {
        const val CLAIMS_KEY = "claims"
    }
}
