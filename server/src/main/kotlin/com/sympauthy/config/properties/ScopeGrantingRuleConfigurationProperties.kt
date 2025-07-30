package com.sympauthy.config.properties

import com.sympauthy.config.properties.ScopeGrantingRuleConfigurationProperties.Companion.RULES_KEY
import io.micronaut.context.annotation.EachProperty

@EachProperty(
    value = RULES_KEY,
    list = true
)
class ScopeGrantingRuleConfigurationProperties() {
    var name: String? = null
    var behavior: String? = null
    var order: String? = null
    var scopes: List<String>? = null
    var expressions: List<String>? = null

    companion object {
        const val RULES_KEY = "rules"
    }
}
