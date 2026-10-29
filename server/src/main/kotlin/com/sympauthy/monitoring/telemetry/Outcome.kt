package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributesBuilder
import kotlinx.coroutines.CancellationException

/**
 * How an event a metric counts ended, recorded under `outcome`, and why it did not succeed, recorded under
 * `error.type`.
 *
 * [Refused] is an ending the caller caused — a wrong password, a code already exchanged — and [Error] the
 * server or a dependency failing. Two attributes rather than a metric per ending, so that summing a metric over
 * every attribute still counts every event.
 */
sealed class Outcome(
    internal val value: String
) {

    /**
     * The error code the caller was answered with, or the class of the failure where it carried none. Null on
     * a success.
     */
    abstract val errorType: String?

    data object Success : Outcome("success") {
        override val errorType: String? = null
    }

    data class Refused(override val errorType: String) : Outcome("refused")

    data class Error(override val errorType: String) : Outcome("error")

    companion object {

        /**
         * An [Error] for a [failure] that carries no code, typed by its class as the semantic conventions type
         * one.
         */
        fun errorOf(failure: Throwable): Error = Error(failure.javaClass.name)
    }
}

internal val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("outcome")
internal val ERROR_TYPE: AttributeKey<String> = AttributeKey.stringKey("error.type")
internal val CLIENT_ID: AttributeKey<String> = AttributeKey.stringKey("client_id")

/**
 * Put [outcome] under `outcome`, and its error type, if it has one, under `error.type`.
 */
internal fun AttributesBuilder.putOutcome(outcome: Outcome): AttributesBuilder {
    put(OUTCOME, outcome.value)
    outcome.errorType?.let { put(ERROR_TYPE, it) }
    return this
}

/**
 * Put [value] under [key], unless it is null: an attribute this server does not know the value of is left out
 * rather than recorded empty.
 */
internal fun AttributesBuilder.putIfNotNull(key: AttributeKey<String>, value: String?): AttributesBuilder {
    if (value != null) put(key, value)
    return this
}

/**
 * These nanoseconds in seconds, the unit every duration is recorded in.
 */
fun Long.nanosToSeconds(): Double = this / NANOS_PER_SECOND

private const val NANOS_PER_SECOND = 1_000_000_000.0

/**
 * Run [block], and hand [onSuccess] how long it took and what it returned, or [onFailure] how long it took and
 * what it threw, which is then rethrown.
 *
 * A cancellation is handed to neither: it says the caller stopped waiting, not how the work ended.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> measureOutcome(
    onSuccess: (elapsedNanos: Long, result: T) -> Unit,
    onFailure: (elapsedNanos: Long, failure: Throwable) -> Unit,
    block: () -> T
): T {
    val start = System.nanoTime()
    val result = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        onFailure(System.nanoTime() - start, failure)
        throw failure
    }
    onSuccess(System.nanoTime() - start, result)
    return result
}
