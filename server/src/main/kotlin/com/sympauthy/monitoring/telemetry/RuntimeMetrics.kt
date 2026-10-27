package com.sympauthy.monitoring.telemetry

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.annotation.PreDestroy
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.lang.management.MemoryUsage

/**
 * The runtime metrics a native image can fill, read from the platform's management beans on every export.
 *
 * Heap memory per pool, thread count, process CPU time and count, under the names OpenTelemetry's semantic
 * conventions give them, and each garbage collector's collection count and accumulated time under this
 * server's own names, since the conventions' `jvm.gc.duration` is built from notifications a native image
 * never sends. `docs/design/telemetry.md` says why nothing else is exported.
 */
@Context
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
class RuntimeMetrics(
    openTelemetry: OpenTelemetry
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val heapPools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
    private val threads = ManagementFactory.getThreadMXBean()
    private val operatingSystem =
        ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
    private val garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans()

    private val instruments: List<AutoCloseable> = listOf(
        heapMemory("jvm.memory.used", "Measure of memory used.") { it.used },
        heapMemory("jvm.memory.committed", "Measure of memory committed.") { it.committed },
        heapMemory("jvm.memory.limit", "Measure of max obtainable memory.") { it.max.takeIf { max -> max >= 0 } },
        meter.upDownCounterBuilder("jvm.thread.count")
            .setDescription("Number of executing platform threads.")
            .setUnit("{thread}")
            .buildWithCallback { measurement ->
                val daemon = threads.daemonThreadCount.toLong()
                measurement.record(daemon, Attributes.of(JVM_THREAD_DAEMON, true))
                measurement.record(threads.threadCount - daemon, Attributes.of(JVM_THREAD_DAEMON, false))
            },
        meter.counterBuilder("jvm.cpu.time")
            .ofDoubles()
            .setDescription("CPU time used by the process as reported by the JVM.")
            .setUnit("s")
            .buildWithCallback { measurement ->
                val nanos = operatingSystem?.processCpuTime ?: -1
                if (nanos >= 0) measurement.record(nanos / NANOS_PER_SECOND)
            },
        meter.upDownCounterBuilder("jvm.cpu.count")
            .setDescription("Number of processors available to the Java virtual machine.")
            .setUnit("{cpu}")
            .buildWithCallback { it.record(Runtime.getRuntime().availableProcessors().toLong()) },
        meter.counterBuilder("sympauthy.gc.collection.count")
            .setDescription("Number of collections each garbage collector has run.")
            .setUnit("{collection}")
            .buildWithCallback { measurement ->
                garbageCollectors.forEach { collector ->
                    val count = collector.collectionCount
                    if (count >= 0) measurement.record(count, Attributes.of(JVM_GC_NAME, collector.name))
                }
            },
        meter.counterBuilder("sympauthy.gc.collection.time")
            .ofDoubles()
            .setDescription("Time each garbage collector has spent collecting, in total.")
            .setUnit("s")
            .buildWithCallback { measurement ->
                garbageCollectors.forEach { collector ->
                    val millis = collector.collectionTime
                    if (millis >= 0) {
                        measurement.record(millis / MILLIS_PER_SECOND, Attributes.of(JVM_GC_NAME, collector.name))
                    }
                }
            },
    )

    /**
     * An instrument named [name] recording what [read] answers of each heap pool's usage, and skipping a pool
     * whose usage, or whose answer, is unknown.
     */
    private fun heapMemory(name: String, description: String, read: (MemoryUsage) -> Long?): AutoCloseable =
        meter.upDownCounterBuilder(name)
            .setDescription(description)
            .setUnit("By")
            .buildWithCallback { measurement ->
                heapPools.forEach { pool ->
                    val value = pool.usage?.let(read) ?: return@forEach
                    measurement.record(
                        value,
                        Attributes.of(JVM_MEMORY_TYPE, HEAP, JVM_MEMORY_POOL_NAME, pool.name)
                    )
                }
            }

    @PreDestroy
    fun close() {
        instruments.forEach(AutoCloseable::close)
    }

    companion object {
        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.runtime"
        private const val HEAP = "heap"
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val MILLIS_PER_SECOND = 1_000.0

        private val JVM_MEMORY_TYPE = AttributeKey.stringKey("jvm.memory.type")
        private val JVM_MEMORY_POOL_NAME = AttributeKey.stringKey("jvm.memory.pool.name")
        private val JVM_THREAD_DAEMON = AttributeKey.booleanKey("jvm.thread.daemon")
        private val JVM_GC_NAME = AttributeKey.stringKey("jvm.gc.name")
    }
}
