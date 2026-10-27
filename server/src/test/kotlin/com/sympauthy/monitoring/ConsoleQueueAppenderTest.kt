package com.sympauthy.monitoring

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.AppenderBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

class ConsoleQueueAppenderTest {

    /** The running one, which carries the MDC adapter a line is prepared for the queue with. */
    private val context = LoggerFactory.getILoggerFactory() as LoggerContext

    /** Holds the queue's thread on its first line, so that the queue fills behind it. */
    private val release = CountDownLatch(1)

    private val blocking = object : AppenderBase<ILoggingEvent>() {
        override fun append(eventObject: ILoggingEvent) {
            release.await()
        }
    }

    private val appender = ConsoleQueueAppender()

    @BeforeEach
    fun start() {
        ConsoleQueueAppender.takeDropped()
        blocking.context = context
        blocking.start()
        appender.context = context
        appender.queueSize = QUEUE_SIZE
        appender.isNeverBlock = true
        appender.addAppender(blocking)
        appender.start()
    }

    @AfterEach
    fun stop() {
        release.countDown()
        appender.stop()
    }

    private fun event(level: Level) =
        LoggingEvent(ConsoleQueueAppenderTest::class.java.name, context.getLogger("test"), level, "line", null, null)

    @Test
    fun `append - Counts the INFO dropped past the discarding threshold and the WARN dropped when full`() {
        // The queue's thread takes one line and holds it; the queue then holds QUEUE_SIZE more.
        appender.doAppend(event(Level.WARN))
        while (appender.numberOfElementsInQueue > 0) Thread.onSpinWait()
        repeat(QUEUE_SIZE - appender.discardingThreshold + 1) { appender.doAppend(event(Level.WARN)) }

        repeat(3) { appender.doAppend(event(Level.INFO)) }
        assertEquals(3, ConsoleQueueAppender.takeDropped())

        repeat(appender.discardingThreshold - 1) { appender.doAppend(event(Level.WARN)) }
        assertEquals(0, ConsoleQueueAppender.takeDropped())

        repeat(2) { appender.doAppend(event(Level.WARN)) }
        assertEquals(2, ConsoleQueueAppender.takeDropped())
    }

    private companion object {
        const val QUEUE_SIZE = 10
    }
}
