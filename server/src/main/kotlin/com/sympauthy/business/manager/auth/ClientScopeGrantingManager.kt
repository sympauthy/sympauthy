package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager in charge of determining which client scopes among the ones requested
 * in a client_credentials flow should be granted or declined.
 *
 * @see [ScopeGrantingRuleManager.applyClientScopeGrantingRules]
 */
@Singleton
class ClientScopeGrantingManager(
    @Inject private val scopeGrantingRuleManager: ScopeGrantingRuleManager,
    @Inject private val featuresConfig: FeaturesConfig
) {

    /**
     * Pass the requested [ClientScope] through the chain of client scope granting methods.
     */
    suspend fun grantClientScopes(
        client: Client,
        requestedScopes: List<ClientScope>
    ): ClientGrantScopesResult {
        val results = mutableListOf<ScopeGrantingMethodResult>()

        // Apply client scope granting rules
        val ruleResult = scopeGrantingRuleManager.applyClientScopeGrantingRules(
            client = client,
            requestedScopes = requestedScopes,
        )
        results.add(ruleResult)

        // Apply default behavior for unhandled scopes
        val unhandledScopes = getUnhandledRequestedScopes(requestedScopes, results)
        val defaultResult = applyDefaultBehavior(unhandledScopes)
        results.add(defaultResult)

        return ClientGrantScopesResult(
            requestedScopes = requestedScopes,
            results = results.toList()
        )
    }

    private fun getUnhandledRequestedScopes(
        requestedScopes: List<Scope>,
        results: List<ScopeGrantingMethodResult>
    ): List<Scope> {
        val unhandledRequestedScopes = requestedScopes.toMutableSet()
        results.forEach {
            unhandledRequestedScopes.removeAll(it.grantedScopes)
            unhandledRequestedScopes.removeAll(it.declinedScopes)
        }
        return unhandledRequestedScopes.toList()
    }

    private fun applyDefaultBehavior(
        requestedScopes: List<Scope>
    ): ScopeGrantingMethodResult {
        val grantUnhandledScopes = featuresConfig.orThrow().grantUnhandledScopes
        return if (grantUnhandledScopes) {
            ScopeGrantingMethodResult(
                grantedScopes = requestedScopes,
                declinedScopes = emptyList()
            )
        } else {
            ScopeGrantingMethodResult(
                grantedScopes = emptyList(),
                declinedScopes = requestedScopes
            )
        }
    }
}

data class ClientGrantScopesResult(
    val requestedScopes: List<Scope>,
    val results: List<ScopeGrantingMethodResult>
) {

    /**
     * List of [Scope] that have been granted after all scope-granting methods have been applied.
     */
    val grantedScopes = results.fold(emptyList<Scope>()) { acc, result -> acc + result.grantedScopes }

    /**
     * List of [Scope] that have been declined after all scope-granting methods have been applied.
     */
    val declinedScopes = results.fold(emptyList<Scope>()) { acc, result -> acc + result.declinedScopes }
}
