package com.sympauthy.config.properties

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

@EachProperty(ScopeConfigurationProperties.SCOPES_KEY)
class ScopeConfigurationProperties(
    @param:Parameter val id: String
) {
    var template: String? = null
    val enabled: String? = null
    var discoverable: String? = null
    var type: String? = null
    var audience: String? = null

    companion object {
        const val SCOPES_KEY = "scopes"
    }
}
