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
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.RequestedClaims
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manages access to collected claims with consent-based scope filtering.
 *
 * Unlike [CollectedClaimManager] which provides unrestricted access for admin and internal use, this
 * manager filters claims on the consented scopes and on the door the caller came through. There are three,
 * and each has a read of its own: the interactive flow, where the person is on this server's own pages
 * having just authenticated; the person's own access token; and a client acting on their behalf. A flag is
 * named for one door and read by none of the others — `docs/design/claims.md` is where they are told apart,
 * and [com.sympauthy.business.model.user.claim.ConsentAcl] is where each is a property.
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
    suspend fun findByUserIdAndReadableByPerson(
        userId: UUID,
        audienceId: String,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        return collectedClaimManager.findByUserId(userId).filter {
            it.claim.belongsToAudience(audienceId) && it.claim.canBeReadByPerson(consentedScopes)
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
     * Return the list of [CollectedClaim] collected from the user identified by [userId] that a client may
     * read, per [findByUserIdAndReadableByClient], and that the deployment publishes in [place].
     *
     * The two questions are asked in that order and neither substitutes for the other: the ACL decides
     * whether this client may know the value at all, and [place] decides which of the values it may know
     * are carried there. So this only ever narrows what the ACL permitted, and a claim the ACL refuses
     * reaches [place] whatever its file says.
     *
     * It is the client's half of the ACL that is asked, for every [place] that answers a client: an id
     * token and an access token are issued to one, and an introspection response is answered to one.
     * `/userinfo` is not among them and reads [findByUserIdAndReadableByPerson] instead, because that
     * endpoint is not client-authenticated.
     *
     * [requestedClaims] is what the `claims` request parameter of the authorization named, and it is asked
     * third and last: it can open a channel the claim's `published-in-when-requested` names and it can do
     * nothing else, so a claim this caller may not read or that belongs to another audience is absent
     * however the request named it. A caller with no request behind it passes [RequestedClaims.NONE] and
     * gets what the deployment publishes for every request.
     */
    suspend fun findByUserIdAndReadableByClientAndPublishedIn(
        userId: UUID,
        audienceId: String,
        place: ClaimPublicationPlace,
        consentedScopes: List<String>,
        clientScopes: List<String> = emptyList(),
        requestedClaims: RequestedClaims = RequestedClaims.NONE
    ): List<CollectedClaim> {
        return findByUserIdAndReadableByClient(
            userId = userId,
            audienceId = audienceId,
            consentedScopes = consentedScopes,
            clientScopes = clientScopes
        ).filter { it.claim.isPublishedIn(place, requestedClaims) }
    }

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId] that the interactive
     * flow collects, given the [consentedScopes].
     *
     * [audienceId] is the audience the answer is for: a claim restricted to another one is left out, and a
     * claim restricted to none is answered whatever the audience. It is not optional, because
     * [consentedScopes] are not: consent is recorded per audience, so scopes a person consented to are always
     * some audience's and the caller holding them knows which.
     *
     * The flow collects a claim at one of two steps, and this answers for both. An identifier claim is
     * collected by the sign-up, so it is answered whatever the ACL says — it is what the account signs in
     * with, and a flow that could not see one would hold a person to a claim it cannot read. Every other
     * claim is collected at the claims step, which is the flow's own half of the ACL: the audience's, within
     * the consented scopes, and marked collected in the flow.
     *
     * No client scopes are taken, because no client is party to this: the person is on this server's own
     * pages having just authenticated. A caller reading through a client's permission instead gets less than
     * the flow offered, and a claim a deployment collects and discloses to no client reads back as never
     * collected.
     */
    suspend fun findByUserIdAndCollectedInFlow(
        userId: UUID,
        audienceId: String,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        val identifierClaims = claimManager.listIdentifierClaims().toSet()
        return collectedClaimManager.findByUserId(userId).filter {
            it.claim in identifierClaims ||
                    (it.claim.belongsToAudience(audienceId) && it.claim.isCollectedInFlow(consentedScopes))
        }
    }

    /**
     * Return the list of [CollectedClaim] collected from the end-user associated to the [session].
     *
     * The claims the flow collects, per [findByUserIdAndCollectedInFlow], within the consented scopes of the
     * session's OAuth2 record and of the audience that authorization is for.
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
                findByUserIdAndCollectedInFlow(
                    userId = userId,
                    audienceId = oauth2Manager.getAudienceId(oauth2),
                    consentedScopes = consentedScopes
                )
            }

            is CompletedInteractiveFlowSession -> {
                val oauth2 = oauth2Manager.fetchOAuth2(session)
                val consentedScopes = oauth2.consentedScopes ?: return emptyList()
                findByUserIdAndCollectedInFlow(
                    userId = session.userId,
                    audienceId = oauth2Manager.getAudienceId(oauth2),
                    consentedScopes = consentedScopes
                )
            }
        }
    }

    /**
     * Return true if every claim this flow holds the end-user to has been collected, for the audience
     * identified by [audienceId] and given the [consentedScopes]. There are two kinds of them, and each
     * arrives by a rule of its own.
     *
     * **An identifier claim is held to whatever its file says.** It is what the account signs in with, so it
     * is required of every account and is collected by the sign-up rather than by the claims step — a
     * password sign-up refuses a value that arrives without one, and a provider sign-up refuses an account
     * the provider asserts none for. Neither `required` nor the flow's own flag is asked of one, and the
     * consent scope is not either: an account holding no value for a claim it is identified by holds a row no
     * login matches, which is not something a client's consent decides.
     *
     * **A required claim is one of three things at once**: it belongs to [audienceId], the end-user consented
     * to the scope it sits under, and the flow collects it. Required claims outside the consented scopes are
     * not considered, since the end-user has not consented to provide them in this authorization flow;
     * required claims restricted to another audience are not either, because they are not this audience's
     * claims to ask for — an audience gets its own required set, and a person signing in to one is not held
     * to another's.
     *
     * [collectedClaims] holds at least the claims the flow collects, per [findByUserIdAndCollectedInFlow].
     * A caller handing over a narrower list answers false for a claim it was never given, and the step this
     * gates is then served again however many times the person submits the value. It may hold more than this
     * answer turns on, which makes no claim look collected that is not.
     *
     * **A claim is collected where it holds a value, not where it holds a row.** A blank submission is a
     * value being cleared and writes a row holding null — see [CollectedClaimUpdate] — so a gate reading
     * presence alone is satisfied by a person posting an empty field, which is the opposite of what
     * `required` asks. An identifier claim cleared is a missing one for the same reason: it holds a row no
     * login matches.
     */
    fun areAllIdentifierAndRequiredClaimsCollectedInFlow(
        collectedClaims: List<CollectedClaim>,
        audienceId: String,
        consentedScopes: List<String>
    ): Boolean {
        val identifierClaims = claimManager.listIdentifierClaims().toSet()
        // The required half leaves identifier claims to the first term rather than answering them twice. It
        // does not change the answer; it is what keeps the flow's own flag unasked of an identifier claim,
        // which is the rule ConsentAcl.collectedInFlow states.
        val heldToClaims = identifierClaims + claimManager.listRequiredClaims()
            .filter { it !in identifierClaims }
            .filter { it.belongsToAudience(audienceId) && it.isCollectedInFlow(consentedScopes) }
        val claimsHoldingAValue = collectedClaims.filter { it.value != null }.map { it.claim }.toSet()
        return heldToClaims.all { it in claimsHoldingAValue }
    }

    /**
     * Update the claims collected for the [user] during the authorization flow, for the audience identified
     * by [audienceId].
     *
     * Only updates targeting claims the flow collects (non-identifier, of that audience and within
     * the [consentedScopes]) are applied. Other updates are silently ignored — a flow may only write what it
     * was entitled to ask for.
     */
    @Transactional
    open suspend fun updateInFlow(
        user: User,
        audienceId: String,
        updates: List<CollectedClaimUpdate>,
        consentedScopes: List<String>
    ): List<CollectedClaim> {
        val collectableClaims = consentAwareClaimManager.listClaimsCollectedInFlowWithScopes(
            audienceId = audienceId,
            consentedScopes = consentedScopes
        )
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
     * **An identifier claim is left out whatever the scopes**, which is the rule [updateInFlow] already gets
     * from [ConsentAwareClaimManager.listClaimsCollectedInFlowWithScopes]. It is what an account signs in with,
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
