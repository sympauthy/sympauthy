package com.sympauthy.platform.server

import io.micronaut.context.annotation.Factory
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Computation

@Factory
class ExecutorServiceFactory {

    /**
     * Provides a thread pool for heavy computation that must be executed outside the main thread.
     */
    @Singleton
    @Computation
    fun provideHeavyComputationExecutorService(): ExecutorService {
        return Executors.newCachedThreadPool()
    }
}
