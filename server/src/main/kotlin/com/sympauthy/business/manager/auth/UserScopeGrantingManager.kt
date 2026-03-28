package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.BuiltInGrantableScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager in charge of determining which user scopes among the ones requested,
 * should be granted or declined.
 *
 * There are multiple methods that can grant/decline the requested scopes:
 * - call the third-party authorization delegation API configured for the client.
 * - scope granting rules will be applied.
 * - decline all.
 *
 * The scopes that have not been granted or declined by a higher order method will be passed to
 * the next one, and the process continues until there is no more scope or no more method.
 *
 * @see [ScopeGrantingRuleManager.applyUserScopeGrantingRules]
 */
@Singleton
class UserScopeGrantingManager(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val scopeGrantingRuleManager: ScopeGrantingRuleManager,
    @Inject private val featuresConfig: FeaturesConfig
) {

    /**
     * Pass the grantable scopes from [AuthorizeAttempt.requestedScopes] through the chain of scope granting methods.
     * Consentable and client scopes are excluded from this pipeline.
     *
     * Built-in grantable scopes marked as [BuiltInGrantableScope.autoGranted] (e.g. `openid`) are automatically
     * granted when requested, without going through the granting rules.
     *
     * Some methods may require access to the claims collected by the authorization flow during the authorization process,
     * it should be provided in the [allCollectedClaims] parameter.
     */
    suspend fun grantScopes(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        allCollectedClaims: List<CollectedClaim>
    ): UserGrantScopesResult {
        val allRequestedScopes = authorizeAttempt.requestedScopes.map {
            scopeManager.findOrThrow(it)
        }
        // Only grantable scopes go through the granting pipeline
        val requestedGrantableScopes = allRequestedScopes.filterIsInstance<GrantableUserScope>()

        // Auto-grant built-in grantable scopes that are marked as auto-granted (e.g. openid)
        val autoGrantedScopeIds = BuiltInGrantableScope.entries
            .filter { it.autoGranted }
            .map { it.scope }
            .toSet()
        val (autoGranted, needsRules) = requestedGrantableScopes.partition { it.scope in autoGrantedScopeIds }

        val results = mutableListOf<ScopeGrantingMethodResult>()
        if (autoGranted.isNotEmpty()) {
            results.add(ScopeGrantingMethodResult(
                grantedScopes = autoGranted,
                declinedScopes = emptyList()
            ))
        }

        getScopeGrantingMethods().forEach { method ->
            val unhandledRequestedScopes = getUnhandledRequestedScopes(
                requestedScopes = needsRules,
                results = results
            )
            val result = method.invoke(
                authorizeAttempt,
                unhandledRequestedScopes,
                allCollectedClaims
            )
            results.add(result)
        }

        return UserGrantScopesResult(
            requestedScopes = requestedGrantableScopes,
            results = results.toList()
        )
    }

    /**
     * Return the list of scope granting methods to apply.
     */
    internal fun getScopeGrantingMethods(): List<suspend (authorizeAttempt: AuthorizeAttempt, requestedScopes: List<Scope>, collectedClaims: List<CollectedClaim>) -> ScopeGrantingMethodResult> {
        return listOf(
            scopeGrantingRuleManager::applyUserScopeGrantingRules,
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

data class UserGrantScopesResult(
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

    /**
     * True if all granted scopes were auto-granted (built-in scopes with autoGranted flag),
     * meaning no granting rules or default behavior contributed any scopes.
     * The first element in [results] is always the auto-granted partition.
     */
    val allAutoGranted: Boolean
        get() = results.drop(1).all { it.grantedScopes.isEmpty() }
}
