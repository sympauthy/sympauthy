package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.claim.Claim
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Provides access to claim definitions filtered by the end-user's consented scopes.
 *
 * Unlike [ClaimManager] which provides unrestricted access to all claim definitions
 * (for admin, configuration, discovery, and internal use), this manager filters claims
 * based on what the end-user has consented to during the authorization flow.
 *
 * Use this manager when listing claims to present to the end-user during the authorization flow.
 * Use [ClaimManager] directly for admin endpoints, OpenID discovery, configuration,
 * entity-to-model mapping, and any context where scope filtering is not applicable.
 */
@Singleton
class ConsentAwareClaimManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
) {

    /**
     * Return the list of [Claim] that should be presented to the end-user for collection
     * during the authorization flow associated to the [session].
     *
     * This excludes:
     * - Claims that are not user-inputted (generated or client-managed).
     * - Identifier claims (e.g. email used for sign-in), which require separate validation.
     * - Claims outside the end-user's consented scopes.
     */
    suspend fun listCollectableClaimsBySession(session: InteractiveFlowSession): List<Claim> {
        return when (session) {
            is FailedInteractiveFlowSession -> emptyList()
            is OnGoingInteractiveFlowSession, is CompletedInteractiveFlowSession -> {
                val consentedScopes = oauth2Manager.fetchOAuth2(session).consentedScopes ?: return emptyList()
                listCollectableClaimsWithScopes(consentedScopes)
            }
        }
    }

    /**
     * Return the list of [Claim] that should be presented to the end-user for collection,
     * given the [consentedScopes].
     *
     * This excludes:
     * - Claims that are not user-inputted (generated or client-managed).
     * - Identifier claims (e.g. email used for sign-in), which require separate validation.
     * - Claims outside the provided [consentedScopes].
     */
    fun listCollectableClaimsWithScopes(consentedScopes: List<String>): List<Claim> {
        val identifierClaims = claimManager.listIdentifierClaims()
        return claimManager.listCollectableClaims()
            .filter { it !in identifierClaims }
            .filter { it.canBeWrittenByUser(consentedScopes) }
    }
}
