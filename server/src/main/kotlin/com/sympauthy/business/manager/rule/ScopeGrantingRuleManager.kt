package com.sympauthy.business.manager.rule

import com.sympauthy.business.model.rule.ScopeGrantingRule
import com.sympauthy.config.model.ScopeGrantingRulesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject

class ScopeGrantingRuleManager(
    @Inject private val uncheckedScopeGrantingRulesConfig: ScopeGrantingRulesConfig,
) {

    /**
     * Return all [ScopeGrantingRule] enabled on this authorization server.
     */
    fun listScopeGrantingRules(): List<ScopeGrantingRule> {
        return uncheckedScopeGrantingRulesConfig.orThrow().scopeGrantingRules
    }
}
