package com.sympauthy.platform.monitoring.telemetry.manager

import kotlinx.coroutines.CancellationException

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
