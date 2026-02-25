package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager in charge of determining which scopes among the one requested by the user,
 * which should be granted or declined.
 *
 * There are multiple methods that can grant/decline the requested scopes:
 * - call the third-party authorization delegation API configured for the client.
 * - scope granting rules will be applied.
 * - decline all.
 *
 * The scopes that have not been granted or declined by a higher order method will be passed to
 * the next one, and the process continues until there is no more scope or no more method.
 *
 * @see [ScopeGrantingRuleManager.applyScopeGrantingRules]
 */
@Singleton
class ScopeGrantingManager(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val scopeGrantingRuleManager: ScopeGrantingRuleManager,
    @Inject private val featuresConfig: FeaturesConfig
) {

    /**
     * Pass the [AuthorizeAttempt.requestedScopes] through the chain of scope granting methods.
     *
     * Some methods may require access to the claims collected by the authorization flow during the authorization process,
     * it should be provided in the [allCollectedClaims] parameter.
     */
    suspend fun grantScopes(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        allCollectedClaims: List<CollectedClaim>
    ): GrantScopesResult {
        val requestedScopes = authorizeAttempt.requestedScopes.map {
            scopeManager.findOrThrow(it)
        }

        val results = mutableListOf<ScopeGrantingMethodResult>()
        getScopeGrantingMethods().forEach { methods ->
            val unhandledRequestedScopes = getUnhandledRequestedScopes(
                requestedScopes = requestedScopes,
                results = results
            )
            val result = methods.invoke(
                authorizeAttempt,
                unhandledRequestedScopes,
                allCollectedClaims
            )
            results.add(result)
        }

        return GrantScopesResult(
            requestedScopes = requestedScopes,
            results = results.toList()
        )
    }

    /**
     * Return the list of scope granting methods to apply.
     */
    internal fun getScopeGrantingMethods(): List<suspend (authorizeAttempt: AuthorizeAttempt, requestedScopes: List<Scope>, collectedClaims: List<CollectedClaim>) -> ScopeGrantingMethodResult> {
        return listOf(
            scopeGrantingRuleManager::applyScopeGrantingRules,
            this::applyDefaultBehavior
        )
    }

    fun getUnhandledRequestedScopes(
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

    internal suspend fun applyDefaultBehavior(
        authorizeAttempt: AuthorizeAttempt,
        requestedScopes: List<Scope>,
        collectedClaims: List<CollectedClaim> = emptyList()
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

data class GrantScopesResult(
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
