package com.sympauthy.business.manager.user

import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.CustomClaim
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manages access to collected claims with consent-based scope filtering.
 *
 * Unlike [CollectedClaimManager] which provides unrestricted access for admin and internal use,
 * this manager filters claims based on the consentedScopes the end-user has consented to share.
 * Custom claims are always included as they are not tied to any OpenID Connect scope.
 */
@Singleton
open class ConsentAwareCollectedClaimManager(
    @Inject private val collectedClaimManager: CollectedClaimManager
) {

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId] and accessible
     * according to the provided [consentedScopes].
     *
     * Standard claims are filtered by scope; custom claims are always included.
     */
    suspend fun findByUserIdAndReadableByScopes(
        userId: UUID,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        return collectedClaimManager.findByUserId(userId).filter {
            it.claim is CustomClaim || it.claim.canBeRead(consentedScopes)
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
                findByUserIdAndReadableByScopes(
                    userId = authorizeAttempt.userId,
                    consentedScopes = authorizeAttempt.consentedScopes
                )
            }

            is CompletedAuthorizeAttempt -> {
                findByUserIdAndReadableByScopes(
                    userId = authorizeAttempt.userId,
                    consentedScopes = authorizeAttempt.consentedScopes
                )
            }
        }
    }

    private fun getApplicableUpdates(
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>
    ): List<CollectedClaimUpdate> {
        return updates.filter { it.claim.canBeWritten(consentedScopes) }
    }

    /**
     * Update the claims collected for the [user] that are writable according to the [consentedScopes],
     * and return all claims readable by those consentedScopes.
     *
     * Updates targeting claims not writable by the given consentedScopes are silently ignored.
     */
    @Transactional
    open suspend fun update(
        user: User,
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        val applicableUpdates = getApplicableUpdates(updates, consentedScopes)
        val collectedClaims = collectedClaimManager.applyUpdates(user, applicableUpdates)
        return collectedClaims.filter {
            it.claim is CustomClaim || it.claim.canBeRead(consentedScopes)
        }
    }
}
