package com.sympauthy.server

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.ShutdownEvent
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Duration.ofSeconds
import kotlin.coroutines.CoroutineContext

/**
 * The scope the server's background work is launched in: what a request does not wait for, and what has to
 * stop when the server does.
 *
 * It is cancelled on [ShutdownEvent], which the context publishes before it destroys any bean, and the stop
 * waits up to [STOP_TIMEOUT] for what was running to unwind. Cancelled from its own `@PreDestroy`, nothing
 * would order it before the connection pool's destruction, so a run's last statements — a job releasing its
 * lease — could reach a pool already closed.
 *
 * A child that fails does not cancel its siblings, and no handler is installed: what is launched here
 * reports its own failure, as a job does through `JobLeaseManager`.
 */
@Singleton
class BackgroundScope : CoroutineScope, ApplicationEventListener<ShutdownEvent> {

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext = Dispatchers.Default + job

    override fun onApplicationEvent(event: ShutdownEvent) {
        runBlocking {
            withTimeoutOrNull(STOP_TIMEOUT.toMillis()) {
                job.cancelAndJoin()
            }
        }
    }

    companion object {

        /**
         * How long the stop waits for the work it cancelled, so a database that never answers does not hold
         * the process open.
         */
        internal val STOP_TIMEOUT: Duration = ofSeconds(5)
    }
}
