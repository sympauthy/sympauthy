package com.sympauthy.monitoring

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.r2dbc.spi.R2dbcBadGrammarException
import io.r2dbc.spi.R2dbcNonTransientResourceException
import io.r2dbc.spi.R2dbcPermissionDeniedException
import io.r2dbc.spi.R2dbcTimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant

class ConditionMonitorTest {

    private val conditionMonitor = ConditionMonitor()

    private val written = ListAppender<ILoggingEvent>()

    private val logger = LoggerFactory.getLogger(ConditionMonitor::class.java) as ch.qos.logback.classic.Logger

    private val start = Instant.parse("2026-10-26T12:00:00Z")

    @BeforeEach
    fun attach() {
        written.start()
        logger.addAppender(written)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(written)
    }

    private fun linesAt(level: Level) = written.list.filter { it.level == level }

    @Test
    fun `reportFailure - Degrades the condition and writes one ERROR however many failures follow`() {
        val failure = IOException("Connection refused")
        repeat(100) { conditionMonitor.reportFailure(Condition.Database, failure, start.plusSeconds(it.toLong())) }

        val errors = linesAt(Level.ERROR)
        assertEquals(1, errors.size)
        assertTrue(errors.single().formattedMessage.startsWith("The database is failing"))
        assertSame(failure, (errors.single().throwableProxy as ch.qos.logback.classic.spi.ThrowableProxy).throwable)
        assertEquals(listOf(DegradedCondition(Condition.Database, start)), conditionMonitor.listDegraded())
    }

    @Test
    fun `reportSuccess - Leaves an operational condition operational and writes nothing`() {
        conditionMonitor.reportSuccess(Condition.Mail, start)

        assertFalse(conditionMonitor.isDegraded(Condition.Mail))
        assertTrue(written.list.isEmpty())
    }

    @Test
    fun `endQuietPeriods - Recovers a condition a quiet period after a success, saying what it absorbed`() {
        conditionMonitor.reportFailure(Condition.Mail, null, start)
        conditionMonitor.reportFailure(Condition.Mail, null, start.plusSeconds(10))
        conditionMonitor.reportSuccess(Condition.Mail, start.plusSeconds(20))

        conditionMonitor.endQuietPeriods(start.plusSeconds(49))
        assertTrue(conditionMonitor.isDegraded(Condition.Mail))

        conditionMonitor.endQuietPeriods(start.plusSeconds(50))
        assertFalse(conditionMonitor.isDegraded(Condition.Mail))
        assertEquals(
            "The mail sender recovered after 50s degraded, having absorbed 2 failures.",
            linesAt(Level.INFO).single().formattedMessage
        )
    }

    @Test
    fun `endQuietPeriods - Does not recover a condition that failed again during its quiet period`() {
        conditionMonitor.reportFailure(Condition.Mail, null, start)
        conditionMonitor.reportSuccess(Condition.Mail, start.plusSeconds(1))
        conditionMonitor.reportFailure(Condition.Mail, null, start.plusSeconds(2))

        conditionMonitor.endQuietPeriods(start.plusSeconds(60))

        assertTrue(conditionMonitor.isDegraded(Condition.Mail))
        assertTrue(linesAt(Level.INFO).isEmpty())
    }

    @Test
    fun `endQuietPeriods - Starts the quiet period on the first success after a failure, not the last`() {
        conditionMonitor.reportFailure(Condition.Mail, null, start)
        conditionMonitor.reportSuccess(Condition.Mail, start.plusSeconds(1))
        conditionMonitor.reportSuccess(Condition.Mail, start.plusSeconds(20))

        conditionMonitor.endQuietPeriods(start.plusSeconds(31))

        assertFalse(conditionMonitor.isDegraded(Condition.Mail))
    }

    @Test
    fun `reportFailure - Writes the ERROR again once a recovered condition fails`() {
        conditionMonitor.reportFailure(Condition.Mail, null, start)
        conditionMonitor.reportSuccess(Condition.Mail, start)
        conditionMonitor.endQuietPeriods(start.plusSeconds(30))
        conditionMonitor.reportFailure(Condition.Mail, null, start.plusSeconds(31))

        assertEquals(2, linesAt(Level.ERROR).size)
        assertEquals(listOf(DegradedCondition(Condition.Mail, start.plusSeconds(31))), conditionMonitor.listDegraded())
    }

    @Test
    fun `reportFailures - Words the console's failures as the lines it dropped`() {
        conditionMonitor.reportFailures(Condition.Console, 120, null, start)
        conditionMonitor.reportSuccess(Condition.Console, start.plusSeconds(1))
        conditionMonitor.endQuietPeriods(start.plusSeconds(31))

        assertEquals(
            "The console's queue recovered after 31s degraded, having dropped 120 lines.",
            linesAt(Level.INFO).single().formattedMessage
        )
    }

    @Test
    fun `absorb - Degrades the database for a connection lost under a query, and answers true`() {
        val lost = IllegalStateException("query failed", R2dbcNonTransientResourceException("Connection closed"))

        assertTrue(conditionMonitor.absorb(lost, start))

        assertTrue(conditionMonitor.isDegraded(Condition.Database))
        assertEquals(1, linesAt(Level.ERROR).size)
    }

    @Test
    fun `absorb - Recognises refused credentials as the database failing`() {
        assertTrue(conditionMonitor.absorb(R2dbcPermissionDeniedException("password authentication failed"), start))
    }

    @Test
    fun `absorb - Writes nothing more for a database already degraded`() {
        conditionMonitor.reportFailure(Condition.Database, null, start)

        assertTrue(conditionMonitor.absorb(R2dbcTimeoutException("timeout"), start.plusSeconds(1)))

        assertEquals(1, linesAt(Level.ERROR).size)
    }

    @Test
    fun `absorb - Answers false for a query that failed on a working connection`() {
        assertFalse(conditionMonitor.absorb(R2dbcBadGrammarException("syntax error"), start))

        assertFalse(conditionMonitor.isDegraded(Condition.Database))
    }

    @Test
    fun `observe - Reports a success when the block returns`() = runTest {
        conditionMonitor.reportFailure(Condition.Provider("google"), null, start)

        val result = conditionMonitor.observe(Condition.Provider("google"), { true }) { "answer" }

        assertEquals("answer", result)
        // The success started the quiet period, which a failure would have reset.
        conditionMonitor.endQuietPeriods(Instant.now().plus(ConditionMonitor.QUIET_PERIOD))
        assertFalse(conditionMonitor.isDegraded(Condition.Provider("google")))
    }

    @Test
    fun `observe - Reports a failure the classifier says is the dependency's, and rethrows it`() = runTest {
        val failure = IOException("Connection refused")

        val thrown = assertThrows<IOException> {
            conditionMonitor.observe(Condition.Provider("google"), { it is IOException }) { throw failure }
        }

        assertSame(failure, thrown)
        assertTrue(conditionMonitor.isDegraded(Condition.Provider("google")))
    }

    @Test
    fun `observe - Reports nothing for a failure the classifier says is not the dependency's`() = runTest {
        assertThrows<IllegalStateException> {
            conditionMonitor.observe(Condition.Provider("google"), { it is IOException }) { error("invalid_grant") }
        }

        assertFalse(conditionMonitor.isDegraded(Condition.Provider("google")))
    }
}
