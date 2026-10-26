package com.sympauthy.cron

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.Conditions
import com.sympauthy.monitoring.ConsoleQueueAppender
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Reports the lines the console's queue dropped, and recovers the conditions whose quiet period has run out.
 *
 * The console's queue is reported from here rather than by the appender because logback builds the appender
 * outside the application context: [ConsoleQueueAppender] counts, and this hands the count to [Conditions].
 * A second without a drop is the queue's success.
 *
 * Every instance runs it for itself, without a lease: what it reports is what this instance observed.
 */
@Singleton
class MonitorConditionsCron(
    @Inject private val conditions: Conditions
) {

    @Scheduled(fixedDelay = "1s")
    fun monitor() {
        val dropped = ConsoleQueueAppender.takeDropped()
        if (dropped > 0) {
            conditions.reportFailures(Condition.Console, dropped)
        } else {
            conditions.reportSuccess(Condition.Console)
        }
        conditions.endQuietPeriods()
    }
}
