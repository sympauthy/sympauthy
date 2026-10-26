package com.sympauthy.monitoring

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * The queue every line below ERROR goes through to the console, counting the lines it drops.
 *
 * It drops exactly what logback's own appender drops, configured as `logback.xml` configures it: a line
 * at INFO or below once the queue is past its discarding threshold, and any line once the queue is full.
 * It counts the line instead of losing it silently, so that [Condition.Console] can say the queue is
 * dropping.
 *
 * **The count is static** because logback builds this appender from its file, before and outside the
 * application context, so it cannot be handed [Conditions]. It is the one place the two meet, and it is
 * read by taking it, with [takeDropped].
 *
 * The count is decided before the line is handed to logback's appender, which decides again: a line lost
 * between two threads racing for the queue's last free place is dropped by logback and counted by
 * neither. The condition still degrades, on the drops on either side of it.
 */
class ConsoleQueueAppender : AsyncAppender() {

    override fun append(eventObject: ILoggingEvent) {
        val remainingCapacity = remainingCapacity
        if (remainingCapacity == 0 || (remainingCapacity < discardingThreshold && isDiscardable(eventObject))) {
            dropped.incrementAndGet()
            return
        }
        super.append(eventObject)
    }

    companion object {
        private val dropped = AtomicLong()

        /**
         * The lines dropped since this was last called.
         */
        fun takeDropped(): Long = dropped.getAndSet(0)
    }
}
