package com.sympauthy.cron

import com.sympauthy.data.Database
import com.sympauthy.data.bean
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Both scheduled cleanups, run against a real database of each dialect.
 *
 * Returning is the whole assertion. A doubled cleaner cannot show whether the transaction, opened on
 * a reactive scheduler and resumed on the thread the cron blocked, comes back at all — and a run that
 * never came back would hold a scheduling thread every quarter of an hour rather than fail.
 *
 * It is the one test here that deletes rows it did not create: running the real cron means removing
 * whatever expired session and abandoned account the shared database holds. That is safe because the
 * suite runs sequentially and every other class unwinds its own rows through `withFixture`, and it is
 * what would have to be reconsidered before these tests are run in parallel.
 */
class ScheduledCleanupCompletesTest {

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `clean - Returns from the expired session cleanup`(database: Database) {
        val cron = database.bean<CleanExpiredInteractiveFlowSessionCron>()

        assertDoesNotThrow { cron.clean() }
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `clean - Returns from the abandoned account sweep`(database: Database) {
        val cron = database.bean<CleanAbandonedAccountCron>()

        assertDoesNotThrow { cron.clean() }
    }
}
