package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.SecurityContextRepository
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
 * It reads the repositories directly rather than through the managers that own them: promoting and
 * collecting are one statement per table over a column no domain concept names, and a pass-through on each
 * of those managers would say less than the list here does.
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
    @Inject private val securityContextRepository: SecurityContextRepository
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
     * Delete the accounts left behind by a sign-up that never completed, and answer how many there were.
     *
     * An account counts as abandoned when the session signing it up is **gone** — see
     * [UserRepository.findAbandoned], which is where that and the rule for skipping an account something
     * still refers to are written. Keying on absence rather than on the sessions one run expired makes this
     * self-correcting: an account orphaned by an earlier failure is collected on the next pass.
     *
     * The account's own rows go first, every one of them rather than only the provisional ones: the account
     * is going, so anything hanging off it is going too. Removing the sessions in the first place belongs to
     * [com.sympauthy.business.manager.flow.InteractiveFlowSessionCleaner], which calls this after it has.
     */
    @Transactional
    open suspend fun deleteAbandoned(): Int {
        val userIds = userRepository.findAbandoned().mapNotNull(UserEntity::id)
        if (userIds.isEmpty()) return 0

        passwordRepository.deleteByUserIdIn(userIds)
        collectedClaimRepository.deleteByUserIdIn(userIds)
        providerUserInfoRepository.deleteByUserIdIn(userIds)
        totpEnrollmentRepository.deleteByUserIdIn(userIds)
        // The places the account was seen in go with it: a security context names the person, and this one
        // never became one. Skipping the account instead would leave every abandoned sign-up that got as
        // far as authenticating uncollected for good.
        securityContextRepository.deleteByUserIdIn(userIds)

        return userRepository.deleteByIdIn(userIds)
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
}
