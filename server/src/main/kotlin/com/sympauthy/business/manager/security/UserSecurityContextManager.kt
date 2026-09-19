package com.sympauthy.business.manager.security

import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.business.model.security.SecurityContextKey
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
 * Keeps the places a person signs in from: records every place a session is driven from against that
 * session, and folds the one a credential was proven at into that person's record once their flow
 * completes.
 *
 * **One manager owns both tables, which departs from the manager-per-attached-record the five other
 * `interactive_flow_session_*` records follow.** Recording and folding are one rule with two halves —
 * what is recorded is only ever read by the fold, and the fold only ever reads what was recorded — and
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
    @Inject private val sessionRepository: InteractiveFlowSessionSecurityContextRepository,
    @Inject private val contextRepository: UserSecurityContextRepository
) {

    private val logger = loggerForClass()

    /**
     * Record that [sessionId] was driven from [observedRequest], having proven nothing.
     *
     * **Call this wherever a request touches an interactive flow session** — where one is created, and
     * where one is resolved from the state a request carried. Nothing about it is gated, because nothing
     * it writes is read against a person: [fold] takes what [stage] stamped and is blind to this by
     * construction, so a row here is what a session knows about itself and is collected with it.
     */
    suspend fun observe(sessionId: UUID, observedRequest: ObservedRequest) {
        record(sessionId, observedRequest, proven = false)
    }

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
     * It stamps the place with the moment of the proof rather than replacing what an earlier step of the
     * same flow recorded, and the fold reads the latest stamp — so a flow proving a credential twice
     * still folds the place the person last proved who they were.
     */
    suspend fun stage(sessionId: UUID, observedRequest: ObservedRequest) {
        record(sessionId, observedRequest, proven = true)
    }

    /**
     * The one write both halves make: bump the place [observedRequest] names, or open it where this
     * session has room for another, and stamp it as proven where [proven].
     *
     * **A session at its bound rolls a place out rather than refusing the new one.** What a session is
     * read for is where it is being driven from now — a stalled sign-in, a step posted from somewhere
     * else — and the place least recently seen is the one that answers that worst, so it is the one that
     * makes room. The bound therefore holds without the freshest sighting being the one it costs.
     *
     * **A place a credential was proven at is the last to go**, because it is the only one the fold ever
     * reads and the only one nobody can write without a credential. Every unproven place is rolled out
     * before it, which is what stops whoever holds a session's state from evicting somebody's proof by
     * presenting user agents.
     */
    private suspend fun record(sessionId: UUID, observedRequest: ObservedRequest, proven: Boolean) {
        val key = observedRequest.securityContextKey()
        val now = LocalDateTime.now()
        try {
            if (writePlace(sessionId, key, observedRequest, now, proven) == 0) {
                // A session presenting more than a handful of distinct places is either a person on a train
                // or somebody enumerating, and neither should be silent.
                logger.warn(
                    "Session $sessionId has been driven from more than $MAX_OBSERVATIONS_PER_SESSION " +
                        "distinct places. The one least recently seen makes room for this one."
                )
                sessionRepository.deleteLeastRecentPlace(sessionId)
                // Once more, and once only: a second empty answer means a concurrent request took the room
                // this one just made, and the line above already says a session is presenting too many.
                writePlace(sessionId, key, observedRequest, now, proven)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            logger.warn("Could not record where session $sessionId was observed from.", failure)
        }
    }

    /**
     * The upsert itself, answering how many rows it moved — zero where the session holds as many distinct
     * places as it may and [key] names none of them.
     */
    private suspend fun writePlace(
        sessionId: UUID,
        key: SecurityContextKey,
        observedRequest: ObservedRequest,
        observedAt: LocalDateTime,
        proven: Boolean
    ): Int = sessionRepository.observe(
        sessionId = sessionId,
        fingerprint = key.fingerprint,
        ip = key.ip,
        userAgent = key.userAgent,
        countryCode = observedRequest.geo?.countryCode,
        regionCode = observedRequest.geo?.regionCode,
        region = observedRequest.geo?.region,
        city = observedRequest.geo?.city,
        timeZone = observedRequest.geo?.timeZone,
        observedDate = observedAt,
        provenDate = if (proven) observedAt else null,
        maxPlaces = MAX_OBSERVATIONS_PER_SESSION
    )

    /**
     * Fold what [sessionId] observed into the places [userId] is known to sign in from, and answer
     * nothing.
     *
     * **Driven by a proven row rather than by the session carrying a user.** A session may hold a user
     * from the moment it was created without anybody having proven who they are — an enrollment an
     * operator started is exactly that, and so is every row written by the mere fact of a request — so a
     * session nothing was proven on records nothing, by construction rather than by a rule somebody has
     * to remember.
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
     * One attempt at the fold: read what this session observed, bump the place its latest proof names or
     * insert it, and consume every row the session holds.
     *
     * Consuming them is what makes a second call do nothing rather than count the same sighting twice, and
     * it is what leaves the cleaner only the observations of flows that never finished. The places nothing
     * was proven at go with them: they are what a session knew about itself, and the session is over.
     *
     * **The sighting is dated when the credential was presented, not when the flow finished.** A flow with
     * an MFA step or a claim to collect completes minutes after the person was observed, and the retention
     * is measured from the sighting — so the proof's own stamp is the one that answers for it.
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
        val observations = sessionRepository.findBySessionId(sessionId)
        if (observations.isEmpty()) {
            return
        }
        val latestProof = observations
            .mapNotNull { observation -> observation.provenDate?.let { observation to it } }
            .maxByOrNull { (_, provenAt) -> provenAt }
        latestProof?.let { (proven, provenAt) -> foldProvenObservation(proven, provenAt, userId) }
        sessionRepository.deleteBySessionIdIn(listOf(sessionId))
    }

    /**
     * Bump the place [proven] names among [userId]'s, or open it where they have not been seen there,
     * dating the sighting [provenAt].
     */
    private suspend fun foldProvenObservation(
        proven: InteractiveFlowSessionSecurityContextEntity,
        provenAt: LocalDateTime,
        userId: UUID
    ) {
        userManager.checkPromoted(userId)

        val existing = contextRepository.findByUserIdAndFingerprint(userId, proven.fingerprint)
        val moved = existing?.let {
            contextRepository.updateLastSeenDate(
                id = it.id!!,
                lastSeenDate = provenAt,
                observationCount = it.observationCount + 1,
                countryCode = proven.countryCode,
                regionCode = proven.regionCode,
                region = proven.region,
                city = proven.city,
                timeZone = proven.timeZone
            )
        } ?: 0
        // An update that moved nothing means the sweep deleted that place between the read and it, which
        // is precisely the sign-in that should have kept it alive — so it is opened again rather than lost.
        if (moved == 0) {
            contextRepository.save(
                UserSecurityContextEntity(
                    userId = userId,
                    fingerprint = proven.fingerprint,
                    ip = proven.ip,
                    userAgent = proven.userAgent,
                    countryCode = proven.countryCode,
                    regionCode = proven.regionCode,
                    region = proven.region,
                    city = proven.city,
                    timeZone = proven.timeZone,
                    firstSeenDate = provenAt,
                    lastSeenDate = provenAt
                )
            )
        }
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
         * How many distinct places one session may hold at once. Generous for a person whose address
         * changes mid-flow — a train, a phone leaving wifi — and short enough that a caller varying the
         * user agent they choose cannot mint a row per request out of a session whose state they hold.
         * Past it a place is rolled out rather than the session growing.
         */
        const val MAX_OBSERVATIONS_PER_SESSION = 10

        /**
         * The insert and, where it lost the race for one place, the update that follows it. A third
         * attempt would be a second loser of a race only two writers can enter.
         */
        const val ATTEMPTS = 2

        /** Bounded, so an exception whose cause is itself cannot hold this open. */
        const val CAUSE_DEPTH = 10
    }
}
