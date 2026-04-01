package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
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
    @Inject private val collectedClaimManager: CollectedClaimManager
) {

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId] and readable
     * by the end-user according to the provided [consentedScopes].
     *
     * Use this method when the caller is the end-user themselves and there is no client authentication
     * (e.g. the OpenID UserInfo endpoint, which is only protected by a bearer token).
     */
    suspend fun findByUserIdAndReadableByUser(
        userId: UUID,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        return collectedClaimManager.findByUserId(userId).filter {
            it.claim.canBeReadByUser(consentedScopes)
        }
    }

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId] and readable
     * by a client according to the provided [consentedScopes].
     *
     * Use this method when the caller is an authenticated client acting on behalf of the user
     * (e.g. authorize attempts, client API endpoints, or token generation).
     */
    suspend fun findByUserIdAndReadableByClient(
        userId: UUID,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        return collectedClaimManager.findByUserId(userId).filter {
            it.claim.canBeReadByClient(consentedScopes)
        }
    }

    /**
     * Return the list of [CollectedClaim] collected from the end-user associated to the [authorizeAttempt].
     *
     * Only the claims that are readable according to the consentedScopes of the [authorizeAttempt] will be returned.
     * Custom claims are always included.
     */
    suspend fun findByAttempt(
        authorizeAttempt: AuthorizeAttempt
    ): List<CollectedClaim> {
        return when (authorizeAttempt) {
            is FailedAuthorizeAttempt -> emptyList()
            is OnGoingAuthorizeAttempt -> {
                if (authorizeAttempt.userId == null || authorizeAttempt.consentedScopes == null) {
                    return emptyList()
                }
                findByUserIdAndReadableByClient(
                    userId = authorizeAttempt.userId,
                    consentedScopes = authorizeAttempt.consentedScopes
                )
            }

            is CompletedAuthorizeAttempt -> {
                findByUserIdAndReadableByClient(
                    userId = authorizeAttempt.userId,
                    consentedScopes = authorizeAttempt.consentedScopes
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
     * The end-user is providing their own data. Only claims writable by the user according to
     * the [consentedScopes] are applied.
     */
    @Transactional
    open suspend fun updateByUser(
        user: User,
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        val applicableUpdates = updates.filter { it.claim.canBeWrittenByUser(consentedScopes) }
        return collectedClaimManager.applyUpdates(user, applicableUpdates)
    }

    /**
     * Update the claims collected for the [user] on behalf of a client.
     *
     * Only claims writable by a client according to the [consentedScopes] are applied.
     * Updates targeting claims not writable by the given scopes are silently ignored.
     * Returns all claims readable by the client for those scopes.
     */
    @Transactional
    open suspend fun updateByClient(
        user: User,
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        val applicableUpdates = updates.filter { it.claim.canBeWrittenByClient(consentedScopes) }
        val collectedClaims = collectedClaimManager.applyUpdates(user, applicableUpdates)
        return collectedClaims.filter { it.claim.canBeReadByClient(consentedScopes) }
    }
}
