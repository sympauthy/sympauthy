package com.sympauthy.monitoring.telemetry

import com.sympauthy.monitoring.telemetry.NettyMetrics.Companion.NETTY_ALLOCATOR_MEMORY_USED
import com.sympauthy.monitoring.telemetry.NettyMetrics.Companion.NETTY_EVENTEXECUTOR_TASKS_PENDING
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.DefaultEventLoopGroup
import io.opentelemetry.api.common.AttributeKey
import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NettyMetricsTest {

    private val metrics = InMemoryMetrics()

    private val loops = DefaultEventLoopGroup(2)

    init {
        NettyMetrics(
            metrics.openTelemetry,
            mockk<EventLoopGroupRegistry> { every { defaultEventLoopGroup } returns loops },
            mockk<ConnectionFactory> { every { metadata.name } returns "H2" }
        )
    }

    @AfterEach
    fun shutDown() {
        loops.shutdownGracefully().syncUninterruptibly()
    }

    @Test
    fun `pending tasks - Records each loop under the name of its thread`() {
        val names = metrics.points(NETTY_EVENTEXECUTOR_TASKS_PENDING).map { it.get(AttributeKey.stringKey("name")) }

        assertEquals(2, names.toSet().size)
    }

    @Test
    fun `memory - Records the allocator's heap and direct memory under Micrometer's attributes`() {
        val points = metrics.points(NETTY_ALLOCATOR_MEMORY_USED)

        assertEquals(setOf("heap", "direct"), points.map { it.get(AttributeKey.stringKey("memory.type")) }.toSet())
        assertEquals(
            setOf("id", "allocator.type", "memory.type"),
            points.flatMap { point -> point.asMap().keys.map { it.key } }.toSet()
        )
    }
}
