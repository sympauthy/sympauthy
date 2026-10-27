package com.sympauthy.monitoring

import com.sympauthy.util.pluralize
import io.r2dbc.spi.R2dbcNonTransientResourceException
import io.r2dbc.spi.R2dbcPermissionDeniedException
import io.r2dbc.spi.R2dbcTimeoutException
import io.r2dbc.spi.R2dbcTransientResourceException

/**
 * A dependency the server cannot fully work without, reported operational or degraded by [ConditionMonitor].
 *
 * This declaration is the authority on which conditions exist: anything reached outside this process on
 * a request's behalf, the console's own queue, whose dropping a line is the server failing to say what it
 * was asked to, and the collector the server's telemetry is exported to.
 *
 * A condition is published on `/health` by its [name] alone, and the names say nothing the sign-in pages
 * do not.
 */
sealed class Condition {

    /**
     * The name the condition is published under, unique among the conditions of its kind.
     */
    abstract val name: String

    /**
     * What a line about the condition opens with.
     */
    internal abstract val subject: String

    /**
     * The sentence the ERROR opens with, saying what stopped working.
     */
    internal open val failingMessage: String get() = "$subject is failing"

    /**
     * [count] failures, worded as what failed, and what they [lost]: each count keyed by the singular noun
     * of what it counts.
     */
    internal open fun describeFailures(count: Long, lost: Map<String, Long>): String =
        "absorbed ${pluralize(count, "failure")}"

    /**
     * Whether [failure], caught wherever it surfaced, is this dependency failing rather than the operation
     * that met it. Only a condition that answers this can be handed a failure by [ConditionMonitor.absorb].
     */
    internal open fun isFailedBy(failure: Throwable): Boolean = false

    data object Database : Condition() {
        override val name = "database"
        override val subject = "The database"

        /**
         * The database could not be reached, did not answer in time, refused this server's credentials, or
         * lost the connection a query was running on. A query that failed on a working connection is not.
         */
        override fun isFailedBy(failure: Throwable): Boolean = generateSequence(failure) { it.cause }.any {
            it is R2dbcNonTransientResourceException || it is R2dbcTransientResourceException ||
                it is R2dbcTimeoutException || it is R2dbcPermissionDeniedException
        }
    }

    data object Mail : Condition() {
        override val name = "mail"
        override val subject = "The mail sender"
    }

    /**
     * The queue every line below ERROR goes through on its way to the console. Its failures are the lines
     * it dropped.
     */
    data object Console : Condition() {
        override val name = "console"
        override val subject = "The console's queue"
        override val failingMessage = "The console's queue is full and dropping lines below ERROR"

        override fun describeFailures(count: Long, lost: Map<String, Long>): String =
            "dropped ${pluralize(count, "line")}"
    }

    /**
     * The collector traces, metrics and log records are exported to. Its failures are the exports it did not
     * accept, and what they lost is the spans and log records in them: a metric is exported as a running
     * total, so the next export that succeeds carries what a failed one did not.
     */
    data object Collector : Condition() {
        override val name = "collector"
        override val subject = "The telemetry collector"

        /**
         * The key a failed export counts the spans it lost under.
         */
        const val SPAN = "span"

        /**
         * The key a failed export counts the log records it lost under.
         */
        const val LOG_RECORD = "log record"

        override fun describeFailures(count: Long, lost: Map<String, Long>): String {
            val failed = "failed ${pluralize(count, "export")}"
            val losses = listOf(SPAN, LOG_RECORD).mapNotNull { noun -> lost[noun]?.let { pluralize(it, noun) } }
            return if (losses.isEmpty()) failed else "$failed and lost ${losses.joinToString(" and ")}"
        }
    }

    data class Provider(val providerId: String) : Condition() {
        override val name get() = providerId
        override val subject get() = "Provider '$providerId'"
    }

    data class AuthorizationWebhook(val clientId: String) : Condition() {
        override val name get() = clientId
        override val subject get() = "The authorization webhook of client '$clientId'"
    }
}
