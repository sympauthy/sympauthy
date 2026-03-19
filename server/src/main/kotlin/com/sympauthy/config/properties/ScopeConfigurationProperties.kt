package com.sympauthy.config.properties

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

@EachProperty(ScopeConfigurationProperties.SCOPES_KEY)
class ScopeConfigurationProperties(
    @param:Parameter val id: String
) {
    val enabled: String? = null
    var discoverable: String? = null
    var type: String? = null

    companion object {
        const val SCOPES_KEY = "scopes"
    }
}
