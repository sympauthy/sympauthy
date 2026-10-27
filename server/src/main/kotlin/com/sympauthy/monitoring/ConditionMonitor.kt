package com.sympauthy.monitoring

import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.toKotlinDuration

/**
 * Whether each [Condition] is operational or degraded, as this instance observed it.
 *
 * **The first failure degrades a condition and writes the ERROR**, synchronously, on the thread that
 * reported it. A failure while it is degraded is only counted.
 *
 * **A condition recovers on a success followed by [QUIET_PERIOD] without a failure**, and says so in one
 * line at INFO, with how long it was degraded and what it absorbed. Without the quiet period a dependency
 * failing one call in two would flip on every call, and write the ERROR per failure by another route.
 * The quiet period runs out when [endQuietPeriods] is next called after it.
 *
 * A success reported while a condition is operational costs a map lookup and nothing else: it is
 * reported on every call a dependency answers.
 *
 * `docs/design/logging.md` is the design this implements.
 */
@Singleton
class ConditionMonitor {

    private val logger = loggerForClass()

    private val states = ConcurrentHashMap<Condition, AtomicReference<State>>()

    /**
     * Report that [condition] failed, with the [failure] that says how, if there is one.
     *
     * Only a failure saying the dependency is unwell is reported; one the caller provoked by what it sent
     * is not.
     */
    fun reportFailure(condition: Condition, failure: Throwable? = null, now: Instant = Instant.now()) {
        reportFailures(condition, 1, failure, now)
    }

    /**
     * Report that [condition] failed [count] times, the last of them with [failure] if there is one.
     */
    fun reportFailures(
        condition: Condition,
        count: Long,
        failure: Throwable? = null,
        now: Instant = Instant.now()
    ) {
        val state = states.computeIfAbsent(condition) { AtomicReference(Operational) }
        while (true) {
            val current = state.get()
            val next = when (current) {
                Operational -> Degraded(since = now, failures = count, quietSince = null)
                is Degraded -> current.copy(failures = current.failures + count, quietSince = null)
            }
            if (state.compareAndSet(current, next)) {
                if (current == Operational) {
                    logger.error(failure) {
                        "${condition.failingMessage}: the server is not fully operational. Every failure " +
                            "until it recovers is counted rather than written."
                    }
                }
                return
            }
        }
    }

    /**
     * Report that [condition] answered. While it is degraded, the first success after a failure starts
     * its quiet period.
     */
    fun reportSuccess(condition: Condition, now: Instant = Instant.now()) {
        val state = states[condition] ?: return
        while (true) {
            val current = state.get()
            if (current !is Degraded || current.quietSince != null) return
            if (state.compareAndSet(current, current.copy(quietSince = now))) return
        }
    }

    /**
     * Report [failure] to the condition it says is failing, if it says one is, and answer whether it did.
     *
     * A caller about to write [failure] at WARN writes nothing when this answers true: a dependency that
     * is down fails every operation that meets it, and its condition already says so once. A condition
     * already degraded is not reported to again, since whatever degraded it may have counted this very
     * failure on its way up.
     */
    fun absorb(failure: Throwable, now: Instant = Instant.now()): Boolean {
        val condition = ABSORBING_CONDITIONS.firstOrNull { it.isFailedBy(failure) } ?: return false
        if (!isDegraded(condition)) {
            reportFailure(condition, failure, now)
        }
        return true
    }

    /**
     * Run [block] against [condition], reporting a success if it returns and a failure if it throws
     * something [isFailure] says the dependency is unwell for. What [block] throws is rethrown either
     * way, and anything else it throws is reported as neither.
     */
    suspend fun <T> observe(
        condition: Condition,
        isFailure: (Throwable) -> Boolean,
        block: suspend () -> T
    ): T {
        val result = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (isFailure(failure)) {
                reportFailure(condition, failure)
            }
            throw failure
        }
        reportSuccess(condition)
        return result
    }

    /**
     * Recover every condition whose quiet period started [QUIET_PERIOD] or more before [now] without a
     * failure since.
     */
    fun endQuietPeriods(now: Instant = Instant.now()) {
        states.forEach { (condition, state) ->
            val current = state.get()
            if (current !is Degraded) return@forEach
            val quietSince = current.quietSince ?: return@forEach
            if (Duration.between(quietSince, now) < QUIET_PERIOD) return@forEach
            if (state.compareAndSet(current, Operational)) {
                val degradedFor = Duration.between(current.since, now).truncatedTo(ChronoUnit.SECONDS)
                logger.info {
                    "${condition.subject} recovered after ${degradedFor.toKotlinDuration()} degraded, having " +
                        "${condition.describeFailures(current.failures)}."
                }
            }
        }
    }

    /**
     * Whether [condition] is degraded now.
     */
    fun isDegraded(condition: Condition): Boolean = states[condition]?.get() is Degraded

    /**
     * The conditions degraded now, each with the moment it degraded.
     */
    fun listDegraded(): List<DegradedCondition> = states.mapNotNull { (condition, state) ->
        (state.get() as? Degraded)?.let { DegradedCondition(condition, it.since) }
    }

    private sealed interface State

    private data object Operational : State

    /**
     * A condition degraded [since] that moment, having absorbed [failures], and quiet since [quietSince]
     * if it has answered since its last failure.
     */
    private data class Degraded(
        val since: Instant,
        val failures: Long,
        val quietSince: Instant?
    ) : State

    companion object {
        /**
         * The conditions a failure caught anywhere can be recognised as.
         */
        private val ABSORBING_CONDITIONS: List<Condition> = listOf(Condition.Database)

        /**
         * How long a degraded condition has to go without a failure after a success to recover, which
         * also bounds the ERROR lines one condition writes to one per period.
         */
        val QUIET_PERIOD: Duration = Duration.ofSeconds(30)
    }
}

/**
 * A [condition] degraded [since] that moment.
 */
data class DegradedCondition(
    val condition: Condition,
    val since: Instant
)
