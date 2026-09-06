package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.ConsentRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.TotpEnrollmentRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.ValidationCodeRepository
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
 * It reads the repositories directly rather than through the managers that own them: promoting and
 * collecting are one statement per table over a column no domain concept names, and a pass-through on each
 * of them would say less than the list here does.
 */
@Singleton
open class ProvisionalAccountManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val userManager: UserManager,
    @Inject private val userRepository: UserRepository,
    @Inject private val passwordRepository: PasswordRepository,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val providerUserInfoRepository: ProviderUserInfoRepository,
    @Inject private val totpEnrollmentRepository: TotpEnrollmentRepository,
    @Inject private val validationCodeRepository: ValidationCodeRepository,
    @Inject private val consentRepository: ConsentRepository,
    @Inject private val authenticationTokenRepository: AuthenticationTokenRepository
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
     * The whole of it — the checks and the five writes — belongs to the caller's transaction, so an account
     * either becomes real in full or stays provisional and is collected. See
     * [com.sympauthy.business.manager.flow.InteractiveFlowEngine].
     */
    @Transactional
    open suspend fun promote(sessionId: UUID, userId: UUID) {
        userRepository.findByIdAndSessionId(userId, sessionId) ?: return

        checkIdentifierClaimsStillFree(userId)
        checkProviderSubjectsStillFree(userId)

        // The satellites before the account itself, so no window exposes an account whose rows are still
        // hidden. Nothing enforces the order — session_id carries no foreign key — but a reader that saw the
        // account first would read it without the claims that identify it.
        passwordRepository.clearSessionId(userId, sessionId)
        collectedClaimRepository.clearSessionId(userId, sessionId)
        providerUserInfoRepository.clearSessionId(userId, sessionId)
        totpEnrollmentRepository.clearSessionId(userId, sessionId)
        userRepository.clearSessionId(userId, sessionId)
    }

    /**
     * Delete the accounts left behind by a sign-up that never completed, taking at most [limit] of them,
     * and answer what the run did — including the ones it deliberately left alone.
     *
     * An account counts as abandoned when the session signing it up is **gone** — see
     * [UserRepository.findCollectable]. Keying on absence rather than on the sessions one run expired makes
     * this self-correcting: an account orphaned by an earlier failure is collected on the next pass, and
     * this sweep needs nothing from the run that expired the session. It is a cleaner of its own, on a cron
     * of its own ([com.sympauthy.cron.CleanAbandonedAccountCron]), rather than a step of the one removing
     * the sessions — which is what keeps it reading an absence every other transaction can see too, and
     * keeps it from holding a session's lock while it waits for an account's. Self-correction is also what
     * makes [limit] safe: a run that fills it leaves the rest to the next one.
     *
     * **An abandoned account an invitation still names is retained, and its id is in the result.**
     * [UserRepository.findRetained] is the rule, and an invitation consumed by an account that never
     * completed is a bug to find rather than a run to lose — so the sweep names them for its caller to
     * report instead of dropping them from a count nobody can tell apart from having had nothing to do.
     * A retained account never counts against [limit]: it is retained for good, so a budget spent on one
     * is a budget spent on it every run for good.
     *
     * **The two answers do not partition the abandoned accounts**, and the gap between them is not a
     * failure to report: an account a session still refers to is neither deleted nor retained, because
     * that session is itself collected and a later run takes the account once it is.
     *
     * The account's rows go first, every one of them rather than only the provisional ones: the account is
     * going, so anything hanging off it is going too. For the four it owns outright that is the same set by
     * the [com.sympauthy.data.model.SessionScoped] invariant — a provisional account owns no committed row.
     * The three beyond them — a validation code, a consent, an issued token — should never have existed
     * against an account no sign-up finished, since writing one refuses a provisional account. Collecting
     * them is what stops one of them holding an account here for good.
     *
     * **Each statement re-asserts provisionality; none of them trusts the read that selected the account.**
     * A flow may promote one of these accounts between that read and these deletes, and an id names a row
     * whatever became of it. Naming the session id instead makes each delete re-check the account as the
     * promotion left it — PostgreSQL through `EvalPlanQual`, H2 through the re-check it runs when the row
     * it locked turns out to have changed — so a promoted account is skipped rather than deleted out from
     * under the flow that finished it. The three that carry no session id of their own spell the same
     * predicate against `users`, which is the weaker half of this: it is read rather than re-checked under
     * a lock, so a promotion committing inside that window is not caught. The row it would take is one that
     * should not have been written.
     */
    @Transactional
    open suspend fun deleteAbandoned(limit: Int): CollectResult {
        val retainedIds = userRepository.findRetained(limit).mapNotNull(UserEntity::id)
        val collectableIds = userRepository.findCollectable(limit).mapNotNull(UserEntity::id)
        if (collectableIds.isEmpty()) return CollectResult(0, retainedIds, filledBatch = false)

        passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(collectableIds)
        collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(collectableIds)
        providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(collectableIds)
        totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(collectableIds)
        validationCodeRepository.deleteByUserIdInAndUserProvisional(collectableIds)
        consentRepository.deleteByUserIdInAndUserProvisional(collectableIds)
        authenticationTokenRepository.deleteByUserIdInAndUserProvisional(collectableIds)

        return CollectResult(
            deletedCount = userRepository.deleteByIdInAndSessionIdIsNotNull(collectableIds),
            retainedIds = retainedIds,
            filledBatch = collectableIds.size == limit
        )
    }

    /**
     * Throw `user.promote.identifier_taken` when a committed account already holds one of the identifier
     * claim values collected for [userId].
     *
     * The same check the sign-up ran, against the same committed-only reader: values are matched across every
     * identifier claim rather than claim by claim, because an end-user may sign in with any of them and a
     * value must therefore be unique across all of them.
     */
    internal suspend fun checkIdentifierClaimsStillFree(userId: UUID) {
        val claimIds = claimManager.listIdentifierClaims().map(Claim::id)
        if (claimIds.isEmpty()) return
        val values = collectedClaimRepository.findByUserIdAndClaimInList(userId, claimIds)
            .mapNotNull(CollectedClaimEntity::value)
        if (values.isEmpty()) return

        if (userManager.isIdentifierValueTaken(claimIds, values)) {
            throw businessExceptionOf(
                detailsId = "user.promote.identifier_taken",
                descriptionId = "description.user.promote.identifier_taken"
            )
        }
    }

    /**
     * Throw `user.promote.provider_subject_taken` when a committed account has meanwhile been linked to one
     * of the third-party identities [userId] holds provisionally.
     */
    internal suspend fun checkProviderSubjectsStillFree(userId: UUID) {
        providerUserInfoRepository.findByUserId(userId).forEach { link ->
            val committed = providerUserInfoRepository.findByProviderIdAndSubjectAndSessionIdIsNull(
                providerId = link.id.providerId,
                subject = link.subject
            )
            if (committed != null) {
                throw businessExceptionOf(
                    detailsId = "user.promote.provider_subject_taken",
                    descriptionId = "description.user.promote.provider_subject_taken",
                    "providerId" to link.id.providerId
                )
            }
        }
    }

    /**
     * What one run of [deleteAbandoned] did.
     */
    data class CollectResult(
        /**
         * How many accounts were deleted. Lower than the number collectable when a flow promoted one
         * between the read and the delete, which is the case the re-asserted session id exists for.
         */
        val deletedCount: Int,
        /**
         * The abandoned accounts a row still refers to, left where they are. Every one of them is a row
         * that outlived the account it belongs to, so the ids are what makes that row findable.
         */
        val retainedIds: List<UUID>,
        /**
         * Whether the sweep took as many collectable accounts as it was allowed, so there are more the
         * next run will take.
         */
        val filledBatch: Boolean
    )
}
