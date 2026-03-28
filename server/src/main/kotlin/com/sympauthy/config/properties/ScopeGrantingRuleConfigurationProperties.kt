package com.sympauthy.config.properties

import io.micronaut.context.annotation.EachProperty

open class ScopeGrantingRuleConfigurationProperties {
    var name: String? = null
    var behavior: String? = null
    var order: String? = null
    var scopes: List<String>? = null
    var expressions: List<String>? = null
}

@EachProperty(
    value = UserScopeGrantingRuleConfigurationProperties.RULES_KEY,
    list = true
)
class UserScopeGrantingRuleConfigurationProperties : ScopeGrantingRuleConfigurationProperties() {
    companion object {
        const val RULES_KEY = "rules.user"
    }
}

@EachProperty(
    value = ClientScopeGrantingRuleConfigurationProperties.RULES_KEY,
    list = true
)
class ClientScopeGrantingRuleConfigurationProperties : ScopeGrantingRuleConfigurationProperties() {
    companion object {
        const val RULES_KEY = "rules.client"
    }
}
