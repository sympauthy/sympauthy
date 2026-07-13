package com.sympauthy.config.properties

import io.micronaut.context.annotation.EachProperty

/**
 * Properties of a single `rules.act_as` rule authorizing a client to act on behalf of a user
 * via OAuth 2.0 Token Exchange (RFC 8693).
 *
 * Act-as rules are permission-only: unlike the scope granting rules, they do not carry a `scopes` list.
 */
@EachProperty(
    value = ActAsRuleConfigurationProperties.RULES_KEY,
    list = true
)
class ActAsRuleConfigurationProperties {
    var name: String? = null
    var behavior: String? = null
    var order: String? = null
    var expressions: List<String>? = null

    companion object {
        const val RULES_KEY = "rules.act_as"
    }
}
