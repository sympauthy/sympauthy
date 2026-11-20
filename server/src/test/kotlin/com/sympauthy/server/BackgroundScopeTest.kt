package com.sympauthy.server

import io.micronaut.context.BeanContext
import io.micronaut.context.event.ShutdownEvent
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackgroundScopeTest {

    private val shutdown = ShutdownEvent(mockk<BeanContext>())

    @Test
    fun `onApplicationEvent - Cancels what is running and waits for it to unwind`() {
        val scope = BackgroundScope()
        val running = CompletableDeferred<Unit>()
        var unwound = false
        val run = scope.launch {
            try {
                running.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    delay(100)
                    unwound = true
                }
            }
        }
        runBlocking { running.await() }

        scope.onApplicationEvent(shutdown)

        assertTrue(run.isCancelled)
        assertTrue(unwound)
    }

    @Test
    fun `onApplicationEvent - Starts nothing launched after the stop`() {
        val scope = BackgroundScope()
        scope.onApplicationEvent(shutdown)
        var ran = false

        val run = scope.launch { ran = true }
        runBlocking { run.join() }

        assertFalse(ran)
    }
}
