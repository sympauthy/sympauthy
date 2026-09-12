package com.sympauthy.business.manager.security

import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.business.model.security.securityContextKey
import com.sympauthy.data.model.InteractiveFlowSessionSecurityContextEntity
import com.sympauthy.data.model.UserSecurityContextEntity
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import com.sympauthy.data.repository.UserSecurityContextRepository
import com.sympauthy.util.loggerForClass
import io.micronaut.transaction.annotation.Transactional
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime
import java.util.*

/**
 * Keeps the places a person signs in from: observes one against the session that saw it, and folds it
 * into that person's record once their flow completes.
 *
 * **One manager owns both tables, which departs from the manager-per-attached-record the five other
 * `interactive_flow_session_*` records follow.** Observing and folding are one rule with two halves —
 * what is staged is only ever read by the fold, and the fold only ever reads what was staged — and
 * splitting them would put that rule in two packages where neither states it.
 *
 * **Neither half may fail a flow.** A person signing in must not be answered with an error because a
 * row nothing yet reads could not be written, so both catch what they cannot finish and say so in the
 * log instead. That is the cost of recording history beside a credential check rather than inside it.
 *
 * **A cancellation is not one of those failures.** It says the request this was recording for is gone, so
 * it travels on rather than being logged as a database that would not answer.
 */
@Singleton
open class UserSecurityContextManager(
    @Inject private val userManager: UserManager,
    @Inject private val stagedRepository: InteractiveFlowSessionSecurityContextRepository,
    @Inject private val contextRepository: UserSecurityContextRepository
) {

    private val logger = loggerForClass()

    /**
     * Record that the person behind [sessionId] was observed at [observedRequest] proving who they are.
     *
     * **Call this only where a credential has verified *and* resolved this session's user.** The
     * password check answers for whatever account matches the login, and a flow whose user is already
     * fixed may reach a step where that answer changes nothing — so a call placed at the verification
     * rather than at the resolution lets anybody holding the session's state, which `docs/security.md`
     * says carries no identity and travels in a URL, write their own address into somebody else's
     * record.
     *
     * It replaces whatever an earlier step of the same flow observed, because the place a person last
     * proved who they were is the one worth keeping.
     */
    suspend fun stage(sessionId: UUID, observedRequest: ObservedRequest) {
        val key = observedRequest.securityContextKey()
        val entity = InteractiveFlowSessionSecurityContextEntity(
            sessionId = sessionId,
            fingerprint = key.fingerprint,
            ip = key.ip,
            userAgent = key.userAgent,
            countryCode = observedRequest.geo?.countryCode,
            regionCode = observedRequest.geo?.regionCode,
            region = observedRequest.geo?.region,
            city = observedRequest.geo?.city,
            timeZone = observedRequest.geo?.timeZone,
            observedDate = LocalDateTime.now()
        )
        try {
            if (stagedRepository.findBySessionId(sessionId) != null) {
                stagedRepository.update(entity)
            } else {
                stagedRepository.save(entity)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            logger.warn("Could not record where session $sessionId was observed from.", failure)
        }
    }

    /**
     * Fold what [sessionId] observed into the places [userId] is known to sign in from, and answer
     * nothing.
     *
     * **Driven by the staged row rather than by the session carrying a user.** A session may hold a
     * user from the moment it was created without anybody having observed the person — an enrollment an
     * operator started is exactly that — so a session nothing staged for records nothing, by
     * construction rather than by a rule somebody has to remember.
     *
     * **Called once the completion transaction has committed, never inside it.** The insert can violate
     * the unique index over `(user_id, fingerprint)`, and inside that transaction the violation would
     * roll back the promotion, the terminal effects and the completion write — answering a person whose
     * sign-in succeeded with a `500`, over a history row. A transaction of its own is also what lets the
     * loser of that race re-read and update, which on PostgreSQL an aborted transaction could not.
     */
    suspend fun fold(sessionId: UUID, userId: UUID) {
        repeat(ATTEMPTS) { attempt ->
            try {
                foldObservation(sessionId, userId)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!failure.isPlaceAlreadyRecorded()) {
                    logger.warn("Could not fold the place session $sessionId was observed from.", failure)
                    return
                }
                // Another completion inserted this same place between the read and the insert. The row is
                // there now, so the attempt after this one takes the update path rather than the insert.
                if (attempt == ATTEMPTS - 1) {
                    logger.warn("Gave up folding the place session $sessionId was observed from.", failure)
                }
            }
        }
    }

    /**
     * One attempt at the fold: read what was staged, bump the place it names or insert it, and consume
     * the staged row.
     *
     * Consuming it is what makes a second call do nothing rather than count the same sighting twice.
     *
     * **The sighting is dated when the credential was presented, not when the flow finished.** A flow with
     * an MFA step or a claim to collect completes minutes after the person was observed, and the retention
     * is measured from the sighting — so the staged row's own stamp is the one that answers for it.
     *
     * The location is overwritten rather than kept, because a place seen again should say where that
     * address is now and not where an edge's database put it the first time. The count is read and
     * written rather than incremented in place, so two folds racing on one row may lose an increment —
     * a stale count on a history row, which is not worth a raw statement of its own.
     *
     * Throws whatever `user.not_promoted` [UserManager.checkPromoted] raises, which cannot happen from
     * the engine's own call: the promotion committed in the transaction before this one.
     */
    @Transactional
    internal open suspend fun foldObservation(sessionId: UUID, userId: UUID) {
        val staged = stagedRepository.findBySessionId(sessionId) ?: return
        userManager.checkPromoted(userId)

        val seenAt = staged.observedDate
        val existing = contextRepository.findByUserIdAndFingerprint(userId, staged.fingerprint)
        val moved = existing?.let {
            contextRepository.updateLastSeenDate(
                id = it.id!!,
                lastSeenDate = seenAt,
                observationCount = it.observationCount + 1,
                countryCode = staged.countryCode,
                regionCode = staged.regionCode,
                region = staged.region,
                city = staged.city,
                timeZone = staged.timeZone
            )
        } ?: 0
        // An update that moved nothing means the sweep deleted that place between the read and it, which
        // is precisely the sign-in that should have kept it alive — so it is opened again rather than lost.
        if (moved == 0) {
            contextRepository.save(
                UserSecurityContextEntity(
                    userId = userId,
                    fingerprint = staged.fingerprint,
                    ip = staged.ip,
                    userAgent = staged.userAgent,
                    countryCode = staged.countryCode,
                    regionCode = staged.regionCode,
                    region = staged.region,
                    city = staged.city,
                    timeZone = staged.timeZone,
                    firstSeenDate = seenAt,
                    lastSeenDate = seenAt
                )
            )
        }
        stagedRepository.deleteBySessionIdIn(listOf(sessionId))
    }

    /**
     * Whether this is the unique index over `(user_id, fingerprint)` refusing a second row for one place.
     *
     * The cause chain is walked rather than the type matched, because the driver's exception reaches here
     * through a transaction interceptor and a coroutine bridge — either of which may wrap it, and a
     * wrapped one would silently turn the retry below into a lost sighting.
     */
    private fun Throwable.isPlaceAlreadyRecorded(): Boolean =
        generateSequence(this, Throwable::cause)
            .take(CAUSE_DEPTH)
            .any { it is R2dbcDataIntegrityViolationException }

    private companion object {

        /**
         * The insert and, where it lost the race for one place, the update that follows it. A third
         * attempt would be a second loser of a race only two writers can enter.
         */
        const val ATTEMPTS = 2

        /** Bounded, so an exception whose cause is itself cannot hold this open. */
        const val CAUSE_DEPTH = 10
    }
}
