package com.sympauthy.data.repository

import com.sympauthy.business.manager.lock.ScheduledJob
import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.JobLeaseEntity
import com.sympauthy.data.withFixture
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime

/**
 * The lease one instance takes over a scheduled job, and the row set the jobs are held to.
 *
 * Every acquisition here runs against a lease of this class's own rather than against one of the seeded
 * ones, so a run of this test leaves no job looking as though an instance were still holding it. It
 * names [ScheduledJob] across the layer boundary on purpose: the enum and the rows the migration seeded
 * are one fact, and a job whose row is missing is a job that never runs.
 */
class JobLeaseRepositoryTest {

    private val name = "job-lease-repository-test"
    private val holder = "job-lease-repository-test-instance"
    private val otherHolder = "job-lease-repository-test-other-instance"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `The migration seeds one row per scheduled job`(database: Database) = withFixture(database) {
        val seeded = repository<JobLeaseRepository>().findAll().map { it.name }.toSet()

        assertEquals(ScheduledJob.entries.map(ScheduledJob::leaseName).toSet(), seeded)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `now - Answers the clock the statements are timed by`(database: Database) = withFixture(database) {
        val leases = repository<JobLeaseRepository>()
        newLease()
        leases.acquire(name, holder, BASE_DATE, BASE_DATE.plusMinutes(10))

        val before = leases.now()
        leases.release(name, holder)
        val after = leases.now()

        val freed = leases.findById(name)!!.expirationDate
        assertTrue(freed in before..after, "The clock read back is not the one the release wrote: $freed.")
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `acquire - Takes a free lease and records who took it`(database: Database) = withFixture(database) {
        val leases = repository<JobLeaseRepository>()
        val taken = BASE_DATE
        newLease()

        val acquired = leases.acquire(name, holder, taken, taken.plusMinutes(10))

        assertEquals(1, acquired)
        val stored = leases.findById(name)!!
        assertEquals(holder, stored.holder)
        assertEquals(taken, stored.acquiredAt)
        assertEquals(taken.plusMinutes(10), stored.expirationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `acquire - Refuses a lease another instance holds`(database: Database) = withFixture(database) {
        val leases = repository<JobLeaseRepository>()
        val taken = BASE_DATE
        newLease()
        leases.acquire(name, holder, taken, taken.plusMinutes(10))

        val acquired = leases.acquire(name, otherHolder, taken.plusMinutes(1), taken.plusMinutes(11))

        assertEquals(0, acquired)
        assertEquals(holder, leases.findById(name)!!.holder)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `acquire - Takes a lease whose holder let it expire`(database: Database) = withFixture(database) {
        val leases = repository<JobLeaseRepository>()
        val taken = BASE_DATE
        newLease()
        leases.acquire(name, holder, taken, taken.plusMinutes(10))

        val acquired = leases.acquire(name, otherHolder, taken.plusMinutes(11), taken.plusMinutes(21))

        assertEquals(1, acquired)
        assertEquals(otherHolder, leases.findById(name)!!.holder)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `release - Frees the lease for the next instance`(database: Database) = withFixture(database) {
        val leases = repository<JobLeaseRepository>()
        val taken = BASE_DATE
        newLease()
        leases.acquire(name, holder, taken, taken.plusMinutes(10))

        val released = leases.release(name, holder)

        assertEquals(1, released)
        assertEquals(1, leases.acquire(name, otherHolder, leases.now(), taken.plusMinutes(12)))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `release - Leaves alone a lease another instance has taken over`(database: Database) =
        withFixture(database) {
            val leases = repository<JobLeaseRepository>()
            val taken = BASE_DATE
            newLease()
            leases.acquire(name, holder, taken, taken.plusMinutes(10))
            leases.acquire(name, otherHolder, taken.plusMinutes(11), taken.plusMinutes(21))

            val released = leases.release(name, holder)

            assertEquals(0, released)
            assertEquals(taken.plusMinutes(21), leases.findById(name)!!.expirationDate)
        }

    /** A lease of this test class's own, free from the epoch as the migration leaves the seeded ones. */
    private suspend fun RepositoryFixture.newLease() {
        val leases = repository<JobLeaseRepository>()
        leases.save(
            JobLeaseEntity(
                holder = null,
                acquiredAt = null,
                expirationDate = LocalDateTime.of(1970, 1, 1, 0, 0)
            ).apply { name = this@JobLeaseRepositoryTest.name }
        )
        deleteOnEnd { leases.deleteById(name) }
    }
}
