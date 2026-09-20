package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
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
     * [audienceId] is the audience the answer is for: a claim restricted to another one is left out, and a
     * claim restricted to none is answered whatever the audience. It is not optional, because
     * [consentedScopes] are not: consent is recorded per audience, so scopes a person consented to are always
     * some audience's and the caller holding them knows which.
     *
     * Use this method when the caller is the end-user themselves and there is no client authentication.
     */
    suspend fun findByUserIdAndReadableByUser(
        userId: UUID,
        audienceId: String,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        return collectedClaimManager.findByUserId(userId).filter {
            it.claim.belongsToAudience(audienceId) && it.claim.canBeReadByUser(consentedScopes)
        }
    }

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId] and readable
     * by a client according to the provided [consentedScopes] and [clientScopes].
     *
     * [audienceId] is the audience the answer is for: a claim restricted to another one is left out, and a
     * claim restricted to none is answered whatever the audience. It is not optional, because
     * [consentedScopes] are not: consent is recorded per audience, so scopes a person consented to are always
     * some audience's and the caller holding them knows which.
     *
     * The two scope lists are read from different places and a claim is readable when either of them allows it:
     * [consentedScopes] are the scopes the end-user consented to, and [clientScopes] are the ones granted to the
     * client itself.
     */
    suspend fun findByUserIdAndReadableByClient(
        userId: UUID,
        audienceId: String,
        consentedScopes: List<String>,
        clientScopes: List<String> = emptyList()
    ): List<CollectedClaim> {
        return collectedClaimManager.findByUserId(userId).filter {
            it.claim.belongsToAudience(audienceId) && it.claim.canBeReadByClient(consentedScopes, clientScopes)
        }
    }

    /**
     * Return the list of [CollectedClaim] collected from the end-user associated to the [session].
     *
     * Only the claims that are readable according to the consented scopes of the session's OAuth2 record will
     * be returned, and only those of the audience that authorization is for. No client scopes are passed since
     * interactive flow sessions operate in the user consent context only.
     */
    suspend fun findBySession(
        session: InteractiveFlowSession
    ): List<CollectedClaim> {
        return when (session) {
            is FailedInteractiveFlowSession, is CancelledInteractiveFlowSession -> emptyList()
            is OnGoingInteractiveFlowSession -> {
                val userId = session.userId ?: return emptyList()
                val oauth2 = oauth2Manager.fetchOAuth2(session)
                val consentedScopes = oauth2.consentedScopes ?: return emptyList()
                findByUserIdAndReadableByClient(
                    userId = userId,
                    audienceId = oauth2Manager.getAudienceId(oauth2),
                    consentedScopes = consentedScopes
                )
            }

            is CompletedInteractiveFlowSession -> {
                val oauth2 = oauth2Manager.fetchOAuth2(session)
                val consentedScopes = oauth2.consentedScopes ?: return emptyList()
                findByUserIdAndReadableByClient(
                    userId = session.userId,
                    audienceId = oauth2Manager.getAudienceId(oauth2),
                    consentedScopes = consentedScopes
                )
            }
        }
    }

    /**
     * Return true if all required claims that the end-user can write given the [consentedScopes]
     * have been collected, for the audience identified by [audienceId].
     *
     * A required claim is one of three things at once: it belongs to [audienceId], the end-user consented to
     * the scope it sits under, and they may write it. Required claims outside the consented scopes are not
     * considered, since the end-user has not consented to provide them in this authorization flow; required
     * claims restricted to another audience are not either, because they are not this audience's claims to ask
     * for — an audience gets its own required set, and a person signing in to one is not held to another's.
     *
     * [collectedClaims] is what the caller has of the end-user, and it may hold more than this answer turns
     * on: an identifier claim is collected whatever the consent. That makes no required claim look collected
     * that is not.
     */
    fun areAllRequiredClaimsCollectedByUser(
        collectedClaims: List<CollectedClaim>,
        audienceId: String,
        consentedScopes: List<String>
    ): Boolean {
        val requiredClaims = claimManager.listRequiredClaims()
            .filter { it.belongsToAudience(audienceId) && it.canBeWrittenByUser(consentedScopes) }
        if (requiredClaims.isEmpty()) {
            return true
        }
        val collectedClaimSet = collectedClaims.map { it.claim }.toSet()
        return requiredClaims.all { it in collectedClaimSet }
    }

    /**
     * Update the claims collected for the [user] during the authorization flow, for the audience identified
     * by [audienceId].
     *
     * Only updates targeting collectable claims (user-inputted, non-identifier, of that audience and within
     * the [consentedScopes]) are applied. Other updates are silently ignored — a flow may only write what it
     * was entitled to ask for.
     */
    @Transactional
    open suspend fun updateByUser(
        user: User,
        audienceId: String,
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        val collectableClaims = consentAwareClaimManager.listCollectableClaimsWithScopes(audienceId, consentedScopes)
        val applicableUpdates = updates.filter { it.claim in collectableClaims }
        return collectedClaimManager.applyUpdates(user, applicableUpdates)
    }

    /**
     * Update the claims collected for the [user] on behalf of a client of the audience identified by
     * [audienceId].
     *
     * Only claims of that audience and writable by a client according to the [consentedScopes] and
     * [clientScopes] are applied. Updates targeting any other claim are silently ignored.
     * Returns all claims of that audience readable by the client for those scopes, which is not the same set:
     * a claim the client may write and may not read is applied and left out of the answer.
     *
     * A claim restricted to another audience is neither written nor answered. The restriction is not a read
     * rule that a write may step around: a client told nothing about a claim must not be able to set it
     * either, or it decides what another audience reads.
     *
     * **An identifier claim is left out whatever the scopes**, which is the rule [updateByUser] already gets
     * from [ConsentAwareClaimManager.listCollectableClaimsWithScopes]. It is what an account signs in with,
     * so a client that could rewrite it could move an account's sign-in to an address it controls — and the
     * account would be none the wiser, since nothing in a claim write verifies the value it stores. The
     * surface says so rather than silently dropping it: see `ClientUserClaimController.updateUserClaims`.
     *
     * As on the read side, [consentedScopes] are the scopes the end-user consented to and [clientScopes] the
     * ones granted to the client itself, and either of the two may be what permits a write.
     */
    @Transactional
    open suspend fun updateByClient(
        user: User,
        audienceId: String,
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>,
        clientScopes: List<String> = emptyList()
    ): List<CollectedClaim> {
        val identifierClaims = claimManager.listIdentifierClaims().toSet()
        val applicableUpdates = updates.filter {
            it.claim !in identifierClaims &&
                it.claim.belongsToAudience(audienceId) &&
                it.claim.canBeWrittenByClient(consentedScopes, clientScopes)
        }
        val collectedClaims = collectedClaimManager.applyUpdates(user, applicableUpdates)
        return collectedClaims.filter {
            it.claim.belongsToAudience(audienceId) && it.claim.canBeReadByClient(consentedScopes, clientScopes)
        }
    }
}
