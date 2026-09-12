package com.sympauthy.business.manager.lock

import com.sympauthy.data.repository.JobLeaseRepository
import com.sympauthy.util.loggerForClass
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Hands one instance the right to run a scheduled job for this tick, and tells the others to skip it.
 *
 * **A lease is not mutual exclusion.** It expires on a clock, so a run that outlasts its own lease runs
 * beside the instance that took it next. What it buys is the work being done once rather than once per
 * instance — so a job taking one must be a job that stays correct when it runs twice, which every
 * cleaner keying on a committed absence already is.
 *
 * The clock is the database's, read back through [JobLeaseRepository.now] rather than taken from this
 * process. It is the only one every instance shares, and a lease timed by each instance's own would let
 * one running fast hold past its duration and one running slow take a lease that is still live.
 *
 * [LockManager] is the mechanism for the other thing, where two writers must not both proceed.
 */
@Singleton
class JobLeaseManager(
    @Inject private val jobLeaseRepository: JobLeaseRepository
) {

    private val logger = loggerForClass()

    /**
     * The instance this manager speaks for, minted when the bean is constructed.
     *
     * It identifies the process for the length of its life and nothing longer: what a lease has to
     * distinguish is a run of this server from a run of the one beside it, and a restart is a different
     * holder of no interest to the row it left behind.
     */
    private val holder = UUID.randomUUID().toString()

    /**
     * Run [block] under the lease [job] holds, and answer whether this instance was the one that ran it.
     *
     * Neither the acquisition nor the release joins a transaction, and [block] runs outside both: a job
     * is minutes of work owning its own writes, and a lease held open across it would make all of them
     * one transaction. So a crash mid-run leaves the lease taken until it expires, which is what its
     * duration is for.
     *
     * The lease is released whatever [block] does, and the failure it threw travels on to the caller —
     * for a scheduled job, to whatever Micronaut does with a task that threw.
     */
    suspend fun withLease(job: ScheduledJob, block: suspend () -> Unit): Boolean {
        val now = jobLeaseRepository.now()
        val acquired = jobLeaseRepository.acquire(
            name = job.leaseName,
            holder = holder,
            now = now,
            expiresAt = now.plus(job.leaseDuration)
        ) == 1

        if (!acquired) {
            logger.debug("Skipping ${job.leaseName}: another instance holds its lease.")
            return false
        }

        try {
            block()
        } finally {
            jobLeaseRepository.release(job.leaseName, holder)
        }
        return true
    }
}
