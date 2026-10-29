package com.sympauthy.business.manager.lock

import com.sympauthy.data.repository.JobLeaseRepository
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.loggerForClass
import com.sympauthy.monitoring.telemetry.JobMetrics
import com.sympauthy.monitoring.telemetry.JobMetrics.JobRunOutcome
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Duration.ofMinutes
import java.util.*

/**
 * Hands one instance the right to run a job, and tells every other instance to skip it.
 *
 * **A lease is not mutual exclusion.** A run whose instance stops renewing runs beside the instance that
 * takes the lease next, because nothing an instance stops doing proves the run stopped with it. What a
 * lease buys is the work being done once rather than once per instance — so a job taking one must be a
 * job that stays correct when it runs twice, which every cleaner keying on a committed absence already
 * is.
 *
 * **A lease expires on how long an instance may be silent, not on how long its job takes.** It is taken
 * for [GRACE_PERIOD] and pushed back every [RENEWAL_PERIOD] by [renewLeases] for as long as this instance
 * is alive, so a slow run keeps its lease and a dead instance's is free within the grace period. Nobody
 * has to guess a job's duration, and guessing it is what a fixed expiry asked for: too short overtakes a
 * slow run, too long leaves a crashed instance's job unrun.
 *
 * The clock is the database's, read back through [JobLeaseRepository.now] rather than taken from this
 * process. It is the only one every instance shares, and a lease timed by each instance's own would let
 * one running fast hold past its grace period and one running slow take a lease that is still live.
 *
 * **A run never throws.** Each one is recorded on `sympauthy.job.run.duration` as it ended — run, failed, or
 * skipped for another instance's lease — and a failure is handed to [ConditionMonitor.absorb], then written at
 * WARN if no condition took it. Thrown, it would end in the default handler of the coroutine the job was
 * launched in, which neither logs it nor counts it.
 *
 * [LockManager] is the mechanism for the other thing, where two writers must not both proceed.
 */
@Singleton
class JobLeaseManager(
    @Inject private val jobLeaseRepository: JobLeaseRepository,
    @Inject private val jobMetrics: JobMetrics,
    @Inject private val conditionMonitor: ConditionMonitor
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
     * one transaction. So a crash mid-run leaves the lease taken until it stops being renewed, which is
     * what the grace period is for.
     *
     * The lease is released whatever [block] does. A failure, of [block] or of the lease itself, is reported
     * as the class says and not thrown; a run that failed after taking the lease still answers true.
     */
    suspend fun withLease(job: LeasedJob, block: suspend () -> Unit): Boolean {
        val start = System.nanoTime()
        var acquired = false
        val outcome = try {
            acquired = acquire(job)
            if (acquired) {
                try {
                    block()
                } finally {
                    jobLeaseRepository.release(job.leaseName, holder)
                }
                JobRunOutcome.SUCCESS
            } else {
                logger.debug { "Skipping ${job.leaseName}: another instance holds its lease." }
                JobRunOutcome.SKIPPED
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report(job.leaseName, failure)
            JobRunOutcome.ERROR
        }
        jobMetrics.recordRun(job.leaseName, System.nanoTime() - start, outcome)
        return acquired
    }

    private suspend fun acquire(job: LeasedJob): Boolean {
        val now = jobLeaseRepository.now()
        return jobLeaseRepository.acquire(
            name = job.leaseName,
            holder = holder,
            now = now,
            expiresAt = now.plus(GRACE_PERIOD)
        ) == 1
    }

    /**
     * Push back the expiry of every lease this instance still holds, and answer how many there were.
     *
     * Zero is the ordinary answer: it renews nothing while no job of this instance's is running, and
     * nothing about it fails when there is nothing to renew.
     *
     * It says that this instance is alive rather than that its run is — which is the most any signal from
     * here could say, since a job hung inside a healthy process goes on being renewed and one frozen with
     * it stops. A lease already expired is left alone rather than pulled back from whoever may have taken
     * it in the meantime: the run carries on, but this instance stops claiming to hold what it may have
     * lost.
     */
    suspend fun renewLeases(): Int {
        val start = System.nanoTime()
        val (renewed, outcome) = try {
            val now = jobLeaseRepository.now()
            val renewed = jobLeaseRepository.renew(
                holder = holder,
                now = now,
                expiresAt = now.plus(GRACE_PERIOD)
            )
            renewed to JobRunOutcome.SUCCESS
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report(RENEW_JOB_LEASES, failure)
            0 to JobRunOutcome.ERROR
        }
        jobMetrics.recordRun(RENEW_JOB_LEASES, System.nanoTime() - start, outcome)
        return renewed
    }

    /**
     * Hand the [failure] of a run of [job] to the condition it says is failing, or write it at WARN.
     */
    private fun report(job: String, failure: Exception) {
        if (!conditionMonitor.absorb(failure)) {
            logger.warn(failure) { "The run of $job failed." }
        }
    }

    companion object {

        /**
         * How long a lease stands without being renewed, which is how long after an instance goes silent
         * before another may take its job. Three renewals, so two may be missed before a live instance is
         * treated as gone.
         */
        internal val GRACE_PERIOD: Duration = ofMinutes(3)

        /** How often [renewLeases] runs, named here because `@Scheduled` needs a literal to read. */
        const val RENEWAL_PERIOD = "1m"

        /**
         * The name a run of [renewLeases] is recorded under, beside the names of the jobs that take a lease.
         */
        const val RENEW_JOB_LEASES = "renew_job_leases"
    }
}
