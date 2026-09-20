package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.TotpEnrollmentRepository
import com.sympauthy.data.repository.UserRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager owning both ends of a provisional account's life: the moment one an interactive flow session
 * signed up becomes an account like any other, and the moment one no session will ever finish is removed.
 *
 * A sign-up spans many requests, so its rows are written against the session that created them and are
 * invisible to every reader that could hand them out (see [com.sympauthy.data.model.SessionScoped]).
 * [promote] is the one write that ends that: it re-checks the uniqueness the sign-up could only check
 * against committed rows, then clears the session id across every table the account owns. [deleteAbandoned]
 * is the other ending, and between them a sign-up is all-or-nothing.
 *
 * It reads the five repositories directly rather than through the managers that own them: promoting and
 * collecting are one statement per table over a column no domain concept names, and a pass-through on each
 * of five managers would say less than the list here does.
 */
@Singleton
open class ProvisionalAccountManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val userManager: UserManager,
    @Inject private val lockManager: LockManager,
    @Inject private val userRepository: UserRepository,
    @Inject private val passwordRepository: PasswordRepository,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val providerUserInfoRepository: ProviderUserInfoRepository,
    @Inject private val totpEnrollmentRepository: TotpEnrollmentRepository
) {

    /**
     * Promote the account [userId] the interactive flow session [sessionId] signed up, making it and every
     * row it owns permanent. Does nothing when that session signed no account up — a sign-in has nothing
     * provisional to promote.
     *
     * Throws a non-recoverable [BusinessException] when another account has taken one of this one's
     * identifiers in the meantime: `user.promote.identifier_taken` for a claim, and
     * `user.promote.provider_subject_taken` for a third-party identity. Neither uniqueness is a database
     * constraint, and both were checked at sign-up against committed rows only, so two sign-ups may hold the
     * same identifier at once. This is where the first of them to complete wins.
     *
     * **The checks are serialised by the identities this account is about to take**, held over the checks
     * and the writes both. Without them two promotions of one address each read committed rows the other
     * has not written yet, both find it free and both commit — and the loser of that race is only found
     * when a sign-in with that address matches two accounts. The loser now waits on the winner instead, and
     * re-checks against what the winner committed. See [LockKey] and `docs/locking-standard.md`.
     *
     * The keys come from a read taken before the lock, because the lock has to name them. What that read
     * cannot miss is another promotion, which is what the keys are for; what it may miss is [deleteAbandoned]
     * collecting this account, and that ends in the rollback below rather than in a promotion of nothing —
     * every write here re-asserts the session id, and the completion write finds no session to mark. They
     * are named in one call because a second one in that transaction is refused.
     *
     * The whole of it — the checks and the five writes — belongs to the caller's transaction, so an account
     * either becomes real in full or stays provisional and is collected, and the locks are held until that
     * transaction ends. What the caller does under them stays database work for that reason. See
     * [com.sympauthy.business.manager.flow.InteractiveFlowEngine].
     */
    @Transactional
    open suspend fun promote(sessionId: UUID, userId: UUID) {
        userRepository.findByIdAndSessionId(userId, sessionId) ?: return

        val claimIds = claimManager.listIdentifierClaims().map(Claim::id)
        val identifierValues = identifierValuesOf(userId, claimIds)
        val links = providerUserInfoRepository.findByUserId(userId)
            .map { ProvisionalLink(providerId = it.id.providerId, subject = it.subject) }

        lockManager.withLock(*keysOver(identifierValues.values.toList(), links)) {
            checkIdentifierClaimsStillFree(userId, claimIds, identifierValues)
            checkProviderSubjectsStillFree(links)

            // The satellites before the account itself, so no window exposes an account whose rows are still
            // hidden. Nothing enforces the order — session_id carries no foreign key — but a reader that saw
            // the account first would read it without the claims that identify it.
            passwordRepository.clearSessionId(userId, sessionId)
            collectedClaimRepository.clearSessionId(userId, sessionId)
            providerUserInfoRepository.clearSessionId(userId, sessionId)
            totpEnrollmentRepository.clearSessionId(userId, sessionId)
            userRepository.clearSessionId(userId, sessionId)
        }
    }

    /**
     * The identifier claim values [userId] holds, by the claim holding them, as `collected_claims` spells
     * them. Keyed by claim because the check below is asked in those pairs; the keys it takes are not.
     */
    private suspend fun identifierValuesOf(userId: UUID, claimIds: List<String>): Map<String, String> {
        if (claimIds.isEmpty()) return emptyMap()
        return collectedClaimRepository.findByUserIdAndClaimInList(userId, claimIds)
            .mapNotNull { claim -> claim.value?.let { claim.claim to it } }
            .toMap()
    }

    /**
     * The identities this promotion is about to make committed, as the keys every other writer of them
     * names. An account holding none — no identifier claim configured, no provider linked — takes nothing,
     * and there is nothing for it to race over.
     */
    private fun keysOver(
        identifierValues: List<String>,
        links: List<ProvisionalLink>
    ): Array<LockKey> = (
        identifierValues.map(LockKey::IdentifierValue) +
            links.map { LockKey.ProviderSubject(it.providerId, it.subject) }
        ).toTypedArray()

    /**
     * Delete the accounts left behind by a sign-up that never completed, and answer how many there were.
     *
     * An account counts as abandoned when the session signing it up is **gone** — see
     * [UserRepository.findAbandoned], which is where that and the rule for skipping an account something
     * still refers to are written. Keying on absence rather than on the sessions one run expired makes this
     * self-correcting: an account orphaned by an earlier failure is collected on the next pass, and this
     * sweep needs nothing from the run that expired the session. It is a cleaner of its own, on a cron of
     * its own ([com.sympauthy.cron.CleanAbandonedAccountCron]), rather than a step of the one removing the
     * sessions — which is what keeps it reading an absence every other transaction can see too, and keeps
     * it from holding a session's lock while it waits for an account's.
     *
     * The account's own rows go first, every one of them rather than only the provisional ones: the account
     * is going, so anything hanging off it is going too. By the [com.sympauthy.data.model.SessionScoped]
     * invariant those are the same set — a provisional account owns no committed row — which is why every
     * one of the five statements can carry the session id and still delete what the account holds.
     *
     * **Each statement re-asserts provisionality; none of them trusts the read that selected the account.**
     * A flow may promote one of these accounts between that read and these deletes, and an id names a row
     * whatever became of it. Naming the session id instead makes each delete re-check the account as the
     * promotion left it — PostgreSQL through `EvalPlanQual`, H2 through the re-check it runs when the row
     * it locked turns out to have changed — so a promoted account is skipped rather than deleted out from
     * under the flow that finished it.
     */
    @Transactional
    open suspend fun deleteAbandoned(): Int {
        val userIds = userRepository.findAbandoned().mapNotNull(UserEntity::id)
        if (userIds.isEmpty()) return 0

        passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(userIds)
        collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(userIds)
        providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(userIds)
        totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(userIds)

        return userRepository.deleteByIdInAndSessionIdIsNotNull(userIds)
    }

    /**
     * Throw `user.promote.identifier_taken` when a committed account already holds one of the
     * [identifierValues] this promotion is about to make committed under one of the identifier claims
     * [claimIds] — [UserManager.findTakenIdentifierOrNull] is the rule. It is asked here a second
     * time in this account's life, against the same committed-only reader, because an account that
     * committed while this one was being signed up was invisible the first time.
     *
     * [userId] is the account being promoted, and exempts nothing: a provisional account owns no committed
     * row, so the reader cannot answer with one of its own. It is named because the rule is written for the
     * caller that holds an account, and this one does.
     *
     * It answers what is committed *now*, which is only worth asking under the lock its caller holds over
     * those values: the account it has to exclude commits between the sign-up's check and this one. What
     * was taken and who won it are named for an operator only; every purpose has resolved by here, so the
     * end-user's half of the failure has no step to send them back to and says nothing about either.
     */
    internal suspend fun checkIdentifierClaimsStillFree(
        userId: UUID,
        claimIds: List<String>,
        identifierValues: Map<String, String>
    ) {
        val taken = userManager.findTakenIdentifierOrNull(userId, claimIds, identifierValues) ?: return
        throw businessExceptionOf(
            detailsId = "user.promote.identifier_taken",
            descriptionId = "description.user.promote.identifier_taken",
            "claim" to taken.claimId,
            "userId" to "${taken.userId}"
        )
    }

    /**
     * Throw `user.promote.provider_subject_taken` when a committed account has meanwhile been linked to one
     * of the third-party identities [links] holds provisionally.
     *
     * One statement per link rather than one over them all: the failure names the provider whose identity
     * was taken, and a single query over every subject could not say which of them lost.
     */
    internal suspend fun checkProviderSubjectsStillFree(links: List<ProvisionalLink>) {
        links.forEach { link ->
            val committed = providerUserInfoRepository.findByProviderIdAndSubjectAndSessionIdIsNull(
                providerId = link.providerId,
                subject = link.subject
            )
            if (committed != null) {
                throw businessExceptionOf(
                    detailsId = "user.promote.provider_subject_taken",
                    descriptionId = "description.user.promote.provider_subject_taken",
                    "providerId" to link.providerId
                )
            }
        }
    }

    /**
     * A third-party identity this promotion holds provisionally: the pair that names it as a key, and all
     * this manager wants of the row carrying it.
     */
    internal data class ProvisionalLink(
        val providerId: String,
        val subject: String
    )
}
