package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manages access to collected claims with consent-based scope filtering.
 *
 * Unlike [CollectedClaimManager] which provides unrestricted access for admin and internal use,
 * this manager filters claims based on the consented scopes and who is performing the operation
 * (the end-user themselves or a client acting on their behalf).
 */
@Singleton
open class ConsentAwareCollectedClaimManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val consentAwareClaimManager: ConsentAwareClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager
) {

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId] and readable
     * by the end-user according to the provided [consentedScopes].
     *
     * When [audienceId] is provided, only claims that belong to that audience (or have no audience restriction)
     * are included.
     *
     * Use this method when the caller is the end-user themselves and there is no client authentication
     * (e.g. the OpenID UserInfo endpoint, which is only protected by a bearer token).
     */
    suspend fun findByUserIdAndReadableByUser(
        userId: UUID,
        consentedScopes: List<String>,
        audienceId: String? = null
    ): List<CollectedClaim> {
        return collectedClaimManager.findByUserId(userId).filter {
            (audienceId == null || it.claim.audienceId == null || it.claim.audienceId == audienceId) &&
                    it.claim.canBeReadByUser(consentedScopes)
        }
    }

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId] and readable
     * by a client according to the provided [consentedScopes] and [clientScopes].
     *
     * When [audienceId] is provided, only claims that belong to that audience (or have no audience restriction)
     * are included.
     *
     * @param consentedScopes scopes the end-user has consented to (e.g. profile, email).
     * @param clientScopes scopes granted to the client itself (e.g. users:claims:read).
     */
    suspend fun findByUserIdAndReadableByClient(
        userId: UUID,
        consentedScopes: List<String>,
        clientScopes: List<String> = emptyList(),
        audienceId: String? = null
    ): List<CollectedClaim> {
        return collectedClaimManager.findByUserId(userId).filter {
            (audienceId == null || it.claim.audienceId == null || it.claim.audienceId == audienceId) &&
                    it.claim.canBeReadByClient(consentedScopes, clientScopes)
        }
    }

    /**
     * Return the list of [CollectedClaim] collected from the end-user associated to the [session].
     *
     * Only the claims that are readable according to the consented scopes of the session's OAuth2 record will
     * be returned. No client scopes are passed since interactive flow sessions operate in the user consent
     * context only.
     */
    suspend fun findBySession(
        session: InteractiveFlowSession
    ): List<CollectedClaim> {
        return when (session) {
            is FailedInteractiveFlowSession -> emptyList()
            is OnGoingInteractiveFlowSession -> {
                val userId = session.userId ?: return emptyList()
                val consentedScopes = oauth2Manager.fetchOAuth2(session).consentedScopes ?: return emptyList()
                findByUserIdAndReadableByClient(
                    userId = userId,
                    consentedScopes = consentedScopes
                )
            }

            is CompletedInteractiveFlowSession -> {
                val consentedScopes = oauth2Manager.fetchOAuth2(session).consentedScopes ?: return emptyList()
                findByUserIdAndReadableByClient(
                    userId = session.userId,
                    consentedScopes = consentedScopes
                )
            }
        }
    }

    /**
     * Return true if all required claims that the end-user can write given the [consentedScopes]
     * have been collected.
     *
     * Required claims outside the consented scopes are not considered, since the end-user
     * has not consented to provide them in this authorization flow.
     */
    fun areAllRequiredClaimsCollectedByUser(
        collectedClaims: List<CollectedClaim>,
        consentedScopes: List<String>
    ): Boolean {
        val requiredClaims = claimManager.listRequiredClaims()
            .filter { it.canBeWrittenByUser(consentedScopes) }
        if (requiredClaims.isEmpty()) {
            return true
        }
        val collectedClaimSet = collectedClaims.map { it.claim }.toSet()
        return requiredClaims.all { it in collectedClaimSet }
    }

    /**
     * Update the claims collected for the [user] during the authorization flow.
     *
     * Only updates targeting collectable claims (user-inputted, non-identifier, within the
     * [consentedScopes]) are applied. Other updates are silently ignored.
     */
    @Transactional
    open suspend fun updateByUser(
        user: User,
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        val collectableClaims = consentAwareClaimManager.listCollectableClaimsWithScopes(consentedScopes)
        val applicableUpdates = updates.filter { it.claim in collectableClaims }
        return collectedClaimManager.applyUpdates(user, applicableUpdates)
    }

    /**
     * Update the claims collected for the [user] on behalf of a client.
     *
     * Only claims writable by a client according to the [consentedScopes] and [clientScopes] are applied.
     * Updates targeting claims not writable by the given scopes are silently ignored.
     * Returns all claims readable by the client for those scopes.
     *
     * @param consentedScopes scopes the end-user has consented to (e.g. profile, email).
     * @param clientScopes scopes granted to the client itself (e.g. users:claims:write).
     */
    @Transactional
    open suspend fun updateByClient(
        user: User,
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>,
        clientScopes: List<String> = emptyList()
    ): List<CollectedClaim> {
        val applicableUpdates = updates.filter { it.claim.canBeWrittenByClient(consentedScopes, clientScopes) }
        val collectedClaims = collectedClaimManager.applyUpdates(user, applicableUpdates)
        return collectedClaims.filter { it.claim.canBeReadByClient(consentedScopes, clientScopes) }
    }
}
