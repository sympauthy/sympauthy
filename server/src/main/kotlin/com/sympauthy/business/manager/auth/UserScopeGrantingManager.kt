package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.*
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
    @Inject private val authorizationWebhookScopeGrantingManager: AuthorizationWebhookUserScopeGrantingManager,
    @Inject private val scopeGrantingRuleManager: ScopeGrantingRuleManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val featuresConfig: FeaturesConfig
) {

    /**
     * Pass the grantable scopes from the session's OAuth2 requested scopes through the chain of scope
     * granting methods. Consentable and client scopes are excluded from this pipeline.
     *
     * Built-in grantable scopes marked as [BuiltInGrantableScope.autoGranted] (e.g. `openid`) are automatically
     * granted when requested, without going through the granting rules.
     *
     * Some methods may require access to the claims collected by the authorization flow during the authorization process,
     * it should be provided in the [allClaims] parameter.
     */
    suspend fun grantScopes(
        session: OnGoingInteractiveFlowSession,
        allClaims: List<CollectedClaim>
    ): UserGrantScopesResult {
        val allRequestedScopes = oauth2Manager.fetchOAuth2(session).requestedScopes.map {
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
            results.add(
                ScopeGrantingMethodResult(
                    grantedScopes = autoGranted,
                    declinedScopes = emptyList()
                )
            )
        }

        getScopeGrantingMethods().forEach { method ->
            val unhandledRequestedScopes = getUnhandledRequestedScopes(
                requestedScopes = needsRules,
                results = results
            )
            val result = method.invoke(
                session,
                unhandledRequestedScopes,
                allClaims
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
    internal fun getScopeGrantingMethods(): List<suspend (session: InteractiveFlowSession, requestedScopes: List<EnabledScope>, collectedClaims: List<CollectedClaim>) -> ScopeGrantingMethodResult> {
        return listOf(
            authorizationWebhookScopeGrantingManager::applyAuthorizationWebhookScopeGranting,
            scopeGrantingRuleManager::applyUserScopeGrantingRules,
            this::applyDefaultBehavior
        )
    }

    fun getUnhandledRequestedScopes(
        requestedScopes: List<EnabledScope>,
        results: List<ScopeGrantingMethodResult>
    ): List<EnabledScope> {
        val unhandledRequestedScopes = requestedScopes.toMutableSet()
        results.forEach {
            unhandledRequestedScopes.removeAll(it.grantedScopes)
            unhandledRequestedScopes.removeAll(it.declinedScopes)
        }
        return unhandledRequestedScopes.toList()
    }

    internal suspend fun applyDefaultBehavior(
        @Suppress("UNUSED_PARAMETER") session: InteractiveFlowSession,
        requestedScopes: List<EnabledScope>,
        collectedClaims: List<CollectedClaim> = emptyList()
    ): ScopeGrantingMethodResult {
        val grantUnhandledScopes = featuresConfig.orThrow().grantUnhandledScopes
        return if (grantUnhandledScopes) {
            ScopeGrantingMethodResult(
                source = GrantedBy.RULE,
                grantedScopes = requestedScopes,
                declinedScopes = emptyList()
            )
        } else {
            ScopeGrantingMethodResult(
                source = GrantedBy.RULE,
                grantedScopes = emptyList(),
                declinedScopes = requestedScopes
            )
        }
    }
}

data class UserGrantScopesResult(
    val requestedScopes: List<EnabledScope>,
    val results: List<ScopeGrantingMethodResult>
) {

    /**
     * List of [EnabledScope] that have been granted after all scope-granting methods have been applied.
     */
    val grantedScopes = results.fold(emptyList<EnabledScope>()) { acc, result -> acc + result.grantedScopes }

    /**
     * List of [EnabledScope] that have been declined after all scope-granting methods have been applied.
     */
    val declinedScopes = results.fold(emptyList<EnabledScope>()) { acc, result -> acc + result.declinedScopes }

    /**
     * True if all granted scopes were auto-granted (built-in scopes with autoGranted flag),
     * meaning no granting method (webhook, rules, default behavior) contributed any scopes.
     */
    val allAutoGranted: Boolean
        get() = results
            .filter { it.source != null }
            .all { it.grantedScopes.isEmpty() }

    /**
     * Determines how the grantable scopes were granted based on the [ScopeGrantingMethodResult.source]
     * of each result that contributed granted scopes.
     * - [GrantedBy.AUTO] if only auto-granted scopes were granted.
     * - [GrantedBy.WEBHOOK] if the authorization webhook contributed granted scopes.
     * - [GrantedBy.RULE] otherwise (rules or default behavior contributed).
     */
    val grantedBy: GrantedBy
        get() = when {
            allAutoGranted -> GrantedBy.AUTO
            results.any { it.source == GrantedBy.WEBHOOK && it.grantedScopes.isNotEmpty() } -> GrantedBy.WEBHOOK
            else -> GrantedBy.RULE
        }
}
