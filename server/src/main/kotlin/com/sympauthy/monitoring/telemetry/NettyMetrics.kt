package com.sympauthy.monitoring.telemetry

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufAllocatorMetricProvider
import io.netty.channel.EventLoopGroup
import io.netty.util.concurrent.SingleThreadEventExecutor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.ObservableLongMeasurement
import io.r2dbc.spi.ConnectionFactory
import jakarta.annotation.PreDestroy
import reactor.netty.resources.LoopResources
import reactor.netty.tcp.TcpResources
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException

/**
 * What Netty holds and what it has still to run, under the names Micrometer's Netty binders give them: the memory
 * its allocator holds, on and off the heap, and the backlog of each event loop.
 *
 * The memory is read from Netty's allocator because a native image has no buffer-pool bean, so nothing else
 * reports what is held outside the heap.
 *
 * The loops are the ones the HTTP server and client run on, and the ones the PostgreSQL driver opens for itself;
 * H2's driver opens none. Each is named by its thread, as Micrometer names it, which starts a loop whose thread
 * has not started yet the first time it is read.
 */
@Context
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
class NettyMetrics(
    openTelemetry: OpenTelemetry,
    eventLoopGroupRegistry: EventLoopGroupRegistry,
    connectionFactory: ConnectionFactory
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val serverLoops = eventLoopGroupRegistry.defaultEventLoopGroup

    /**
     * Whether the PostgreSQL driver runs on loops of its own, which it takes from reactor-netty's shared
     * resources when it opens its first connection.
     */
    private val hasDatabaseLoops = connectionFactory.metadata.name == POSTGRESQL

    private val loopNames = ConcurrentHashMap<SingleThreadEventExecutor, String>()

    private val instruments: List<AutoCloseable> = listOf(
        meter.upDownCounterBuilder(NETTY_ALLOCATOR_MEMORY_USED)
            .setDescription("Memory Netty's allocator holds, on and off the heap.")
            .setUnit("By")
            .buildWithCallback { measurement ->
                val allocator = ByteBufAllocator.DEFAULT
                val metric = (allocator as? ByteBufAllocatorMetricProvider)?.metric() ?: return@buildWithCallback
                val attributes = Attributes.builder()
                    .put(ID, allocator.hashCode().toString())
                    .put(ALLOCATOR_TYPE, allocator.javaClass.simpleName)
                measurement.record(metric.usedHeapMemory(), attributes.put(MEMORY_TYPE, HEAP).build())
                measurement.record(metric.usedDirectMemory(), attributes.put(MEMORY_TYPE, DIRECT).build())
            },
        meter.gaugeBuilder(NETTY_EVENTEXECUTOR_TASKS_PENDING)
            .ofLongs()
            .setDescription("The number of pending tasks in individual event executors.")
            .setUnit("{task}")
            .buildWithCallback { measurement ->
                recordBacklogs(serverLoops, measurement)
                if (hasDatabaseLoops) {
                    recordBacklogs(TcpResources.get().onClient(LoopResources.DEFAULT_NATIVE), measurement)
                }
            },
    )

    /**
     * Record the queue of each loop of [group] under the name of its thread, leaving out a loop that has shut down.
     */
    private fun recordBacklogs(group: EventLoopGroup, measurement: ObservableLongMeasurement) {
        group.filterIsInstance<SingleThreadEventExecutor>().forEach { loop ->
            val name = try {
                loopNames.getOrPut(loop) { loop.threadProperties().name() }
            } catch (_: RejectedExecutionException) {
                return@forEach
            }
            measurement.record(loop.pendingTasks().toLong(), Attributes.of(NAME, name))
        }
    }

    @PreDestroy
    fun close() {
        instruments.forEach(AutoCloseable::close)
    }

    companion object {
        const val NETTY_ALLOCATOR_MEMORY_USED = "netty.allocator.memory.used"
        const val NETTY_EVENTEXECUTOR_TASKS_PENDING = "netty.eventexecutor.tasks.pending"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.netty"
        private const val POSTGRESQL = "PostgreSQL"
        private const val DIRECT = "direct"
        private const val HEAP = "heap"

        private val ID = AttributeKey.stringKey("id")
        private val ALLOCATOR_TYPE = AttributeKey.stringKey("allocator.type")
        private val MEMORY_TYPE = AttributeKey.stringKey("memory.type")
        private val NAME = AttributeKey.stringKey("name")
    }
}
