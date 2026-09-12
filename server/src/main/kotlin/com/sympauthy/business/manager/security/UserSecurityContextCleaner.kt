package com.sympauthy.business.manager.security

import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.data.model.UserSecurityContextEntity
import com.sympauthy.data.repository.UserSecurityContextRepository
import com.sympauthy.util.loggerForClass
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Deletes the places nobody has signed in from for longer than the deployment keeps them.
 *
 * **The cutoff is computed at sweep time rather than stamped on each row**, which is what makes lowering
 * `advanced.security-context.known-user-retention` take effect on the next run instead of on each row's
 * next sighting — the rows an operator lowered it for being exactly the ones nobody is signing in from.
 *
 * **It claims a bounded batch rather than issuing one `DELETE`**, because the size of that statement is
 * whatever an operator's retention change makes it and `DELETE … LIMIT` is no part of PostgreSQL. The
 * claim is the one in `docs/locking-standard.md`.
 */
@Singleton
open class UserSecurityContextCleaner(
    @Inject private val contextRepository: UserSecurityContextRepository,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig
) {

    private val logger = loggerForClass()

    /**
     * Delete what has expired and answer how many rows went.
     *
     * Answers zero without touching the table where the configuration did not parse — the retention is the
     * only thing that says what expired means, and guessing one would delete somebody's history on a number
     * no operator chose — and says so, because a sweep that never runs keeps addresses indefinitely.
     */
    suspend fun clean(): Int {
        val retention = (uncheckedAdvancedConfig as? EnabledAdvancedConfig)
            ?.securityContext
            ?.knownUserRetention
        if (retention == null) {
            // Said rather than passed over in silence: this is the one job whose not running keeps personal
            // data past the point an operator asked for it to go, and silence there is indistinguishable
            // from a table that had nothing to delete.
            logger.warn(
                "Not deleting expired security contexts: the configuration did not parse, so no retention " +
                    "says what expired means. Readiness reports which keys are at fault."
            )
            return 0
        }

        val cutoff = LocalDateTime.now().minus(retention)
        var deleted = 0
        repeat(MAX_BATCHES) {
            val batch = deleteBatchBefore(cutoff)
            deleted += batch.deleted
            if (batch.claimed < BATCH_SIZE) return deleted
        }
        logger.debug("Stopped after $MAX_BATCHES batches; the remaining expired contexts go on the next run.")
        return deleted
    }

    /**
     * Claim up to [BATCH_SIZE] expired rows and delete them, in one transaction, answering what it did.
     *
     * **The delete re-asserts the predicate the claim selected by.** The fold writes this table too, so a
     * place seen again between the claim and the delete has to survive it — an identifier alone names a
     * row whatever became of it.
     */
    @Transactional
    internal open suspend fun deleteBatchBefore(cutoff: LocalDateTime): BatchResult {
        val claimed = contextRepository.claimExpired(cutoff, BATCH_SIZE)
        if (claimed.isEmpty()) return BatchResult(claimed = 0, deleted = 0)
        val deleted = contextRepository.deleteByIdInAndLastSeenDateLessThan(
            ids = claimed.mapNotNull(UserSecurityContextEntity::id),
            cutoff = cutoff
        )
        return BatchResult(claimed = claimed.size, deleted = deleted)
    }

    /**
     * What one batch took and what it removed, which differ by the rows somebody signed in from again
     * while the batch was in flight.
     *
     * The loop keys on [claimed] rather than on [deleted]: a run that asked for a full batch and deleted
     * fewer has not reached the end of the table.
     */
    internal data class BatchResult(
        val claimed: Int,
        val deleted: Int
    )

    private companion object {

        /** How many rows one transaction deletes, so no run holds a statement whose size a setting chose. */
        const val BATCH_SIZE = 1_000

        /**
         * How many batches one run takes before leaving the rest to the next. A bound rather than a
         * `while (true)`, so a table being written as fast as it is swept cannot hold a run open.
         */
        const val MAX_BATCHES = 100
    }
}
