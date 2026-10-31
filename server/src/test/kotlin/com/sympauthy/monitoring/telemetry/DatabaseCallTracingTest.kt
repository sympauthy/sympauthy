package com.sympauthy.monitoring.telemetry

import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE
import io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE
import io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY
import io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT
import io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE
import io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME
import io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE
import io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS
import io.r2dbc.proxy.core.MethodExecutionInfo
import io.r2dbc.proxy.core.QueryExecutionInfo
import io.r2dbc.proxy.core.QueryInfo
import io.r2dbc.proxy.core.ValueStore
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST
import io.r2dbc.spi.Option
import io.r2dbc.spi.R2dbcException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList

@ExtendWith(MockKExtension::class)
class DatabaseCallTracingTest {

    private val exported = CopyOnWriteArrayList<SpanData>()

    private val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(object : SpanExporter {
            override fun export(spans: Collection<SpanData>): CompletableResultCode {
                exported.addAll(spans)
                return CompletableResultCode.ofSuccess()
            }

            override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

            override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
        }))
        .build()

    private val openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()

    private val tracer = openTelemetry.getTracer("test")

    private val connectionFactory = DatabaseCallTracingConnectionFactoryFactory().connectionFactory(
        ConnectionFactoryOptions.parse("r2dbc:h2:mem:///query-tracing-test?DB_CLOSE_DELAY=-1"),
        openTelemetry
    )

    @AfterEach
    fun tearDown() {
        tracerProvider.close()
    }

    private fun execute(statement: String, vararg values: Any): Mono<Void> = Mono.usingWhen(
        connectionFactory.create(),
        { connection ->
            val prepared = connection.createStatement(statement)
            values.forEachIndexed { index, value -> prepared.bind(index, value) }
            Flux.from(prepared.execute()).flatMap { it.rowsUpdated }.then()
        },
        { it.close() }
    )

    /**
     * Run [block] under a span of its own, as a request is served, and return that span.
     */
    private fun underRequest(block: () -> Unit): SpanData {
        val request = tracer.spanBuilder("GET /request").setSpanKind(SpanKind.SERVER).startSpan()
        request.makeCurrent().use { block() }
        request.end()
        return exported.single { it.name == "GET /request" }
    }

    private fun querySpans(): List<SpanData> = exported.filter { it.attributes.get(DB_QUERY_TEXT) != null }

    private fun roundTripSpans(): List<SpanData> =
        exported.filter { it.kind == SpanKind.CLIENT && it.attributes.get(DB_QUERY_TEXT) == null }

    @Test
    fun `connectionFactory - Opens a client span for a query, under the span of the request that made it`() {
        val request = underRequest { execute("SELECT 1").block() }

        val query = querySpans().single()
        assertEquals(request.spanContext.traceId, query.traceId)
        assertEquals(request.spanContext.spanId, query.parentSpanId)
        assertEquals("h2database", query.attributes.get(DB_SYSTEM_NAME))
        assertEquals("query-tracing-test", query.attributes.get(DB_NAMESPACE))
        assertNull(query.attributes.get(SERVER_ADDRESS))
        assertEquals("SELECT", query.name)
    }

    @Test
    fun `connectionFactory - Replaces every value a statement holds, and keeps the identifiers it quotes`() {
        execute("""CREATE TABLE IF NOT EXISTS "people" ("email" VARCHAR(255), "age" INT)""").block()

        underRequest {
            execute("""INSERT INTO "people" ("email", "age") VALUES ('person@example.org', 42)""").block()
            execute("""SELECT "email" FROM "people" WHERE "email" = $1""", "person@example.org").block()
        }

        val (insert, select) = querySpans()
        assertEquals("""INSERT INTO "people" ("email", "age") VALUES (?, ?)""", insert.attributes.get(DB_QUERY_TEXT))
        assertEquals("""INSERT "people"""", insert.attributes.get(DB_QUERY_SUMMARY))
        assertEquals("""SELECT "email" FROM "people" WHERE "email" = $1""", select.attributes.get(DB_QUERY_TEXT))
        assertEquals("""SELECT "people"""", select.name)
        assertFalse(exported.any { span -> "person@example.org" in span.attributes.asMap().values.toString() })
    }

    @Test
    fun `connectionFactory - Opens no span for a query made outside any request`() {
        execute("SELECT 1").block()

        assertTrue(exported.isEmpty())
    }

    @Test
    fun `connectionFactory - Finds the request's span in the reactive chain when the thread carries none`() {
        val request = tracer.spanBuilder("GET /request").setSpanKind(SpanKind.SERVER).startSpan()
        val requestContext = Context.root().with(request)

        execute("SELECT 1")
            .contextWrite { ReactorPropagation.addContextElement(it, OpenTelemetryPropagationContext(requestContext)) }
            .block()
        request.end()

        assertFalse(Span.current().spanContext.isValid)
        assertEquals(request.spanContext.spanId, querySpans().single().parentSpanId)
    }

    @Test
    fun `connectionFactory - Prefers the request's span the reactive chain carries over the thread's`() {
        val request = tracer.spanBuilder("GET /request").setSpanKind(SpanKind.SERVER).startSpan()
        val requestContext = OpenTelemetryPropagationContext(Context.root().with(request))
        val unrelated = tracer.spanBuilder("unrelated").startSpan()

        unrelated.makeCurrent().use {
            execute("SELECT 1")
                .contextWrite { ReactorPropagation.addContextElement(it, requestContext) }
                .block()
        }
        request.end()
        unrelated.end()

        assertEquals(request.spanContext.spanId, querySpans().single().parentSpanId)
    }

    @Test
    fun `connectionFactory - Records how many rows a statement bound at once`() {
        execute("""CREATE TABLE IF NOT EXISTS "batched" ("id" INT)""").block()

        underRequest {
            Mono.usingWhen(
                connectionFactory.create(),
                { connection ->
                    val statement = connection.createStatement("""INSERT INTO "batched" ("id") VALUES ($1)""")
                        .bind(0, 1)
                        .add()
                        .bind(0, 2)
                    Flux.from(statement.execute()).flatMap { it.rowsUpdated }.then()
                },
                { it.close() }
            ).block()
        }

        assertEquals(2L, querySpans().single().attributes.get(DB_OPERATION_BATCH_SIZE))
    }

    @Test
    fun `afterQuery - Ends as a failure a query cancelled before any result`() {
        val listener = DatabaseCallTracingListener(
            databaseCallInstrumenter(openTelemetry),
            QueriedDatabase("h2database", "sympauthy", null, null)
        )
        val store = ValueStore.create()
        val execution = mockk<QueryExecutionInfo> {
            every { queries } returns listOf(QueryInfo("SELECT 1"))
            every { batchSize } returns 0
            every { bindingsSize } returns 1
            every { valueStore } returns store
            every { throwable } returns null
            every { isSuccess } returns false
        }

        underRequest {
            listener.beforeQuery(execution)
            listener.afterQuery(execution)
        }

        val query = querySpans().single()
        assertEquals(StatusCode.ERROR, query.status.statusCode)
        assertEquals(CancellationException::class.java.name, query.attributes.get(ERROR_TYPE))
    }

    @Test
    fun `connectionFactory - Ends as a success a query cancelled once it produced a result`() {
        underRequest {
            Mono.usingWhen(
                connectionFactory.create(),
                { connection ->
                    Flux.from(connection.createStatement("SELECT 1 UNION ALL SELECT 2").execute())
                        .flatMap { result -> result.map { row, _ -> row.get(0) } }
                        .next()
                },
                { it.close() }
            ).block()
        }

        val query = querySpans().single()
        assertEquals(StatusCode.UNSET, query.status.statusCode)
        assertNull(query.attributes.get(ERROR_TYPE))
    }

    @Test
    fun `connectionFactory - Records the SQL state of a query the database refused`() {
        val failure = assertThrows<R2dbcException> {
            underRequest { execute("""SELECT "nothing" FROM "no_such_table"""").block() }
        }

        val query = querySpans().single()
        assertEquals(failure.sqlState, query.attributes.get(DB_RESPONSE_STATUS_CODE))
        assertEquals(failure.sqlState, query.attributes.get(ERROR_TYPE))
    }

    @Test
    fun `connectionFactory - Opens a span for the connection a request waits for, ended before its first query`() {
        val request = underRequest { execute("SELECT 1").block() }

        val connect = roundTripSpans().single()
        assertEquals("connect", connect.name)
        assertEquals(request.spanContext.spanId, connect.parentSpanId)
        assertEquals("h2database", connect.attributes.get(DB_SYSTEM_NAME))
        assertEquals("query-tracing-test", connect.attributes.get(DB_NAMESPACE))
        assertTrue(connect.endEpochNanos <= querySpans().single().startEpochNanos)
    }

    @Test
    fun `connectionFactory - Opens a span for a transaction beginning and one for it committing`() {
        val request = underRequest { inTransaction { it.commitTransaction() }.block() }

        assertEquals(listOf("connect", "BEGIN", "COMMIT"), roundTripSpans().map(SpanData::getName))
        assertTrue(roundTripSpans().all { it.parentSpanId == request.spanContext.spanId })
    }

    @Test
    fun `connectionFactory - Opens a span for a transaction rolling back`() {
        underRequest { inTransaction { it.rollbackTransaction() }.block() }

        assertEquals(listOf("connect", "BEGIN", "ROLLBACK"), roundTripSpans().map(SpanData::getName))
    }

    @Test
    fun `afterMethod - Ends the span of a round trip once, however many times it is reported`() {
        val listener = DatabaseCallTracingListener(
            databaseCallInstrumenter(openTelemetry),
            QueriedDatabase("h2database", "sympauthy", null, null)
        )
        val store = ValueStore.create()
        val execution = mockk<MethodExecutionInfo> {
            every { method } returns Connection::class.java.getMethod("commitTransaction")
            every { valueStore } returns store
            every { thrown } returns null
        }

        val request = tracer.spanBuilder("GET /request").startSpan()
        request.makeCurrent().use {
            listener.beforeMethod(execution)
            listener.afterMethod(execution)
            listener.afterMethod(execution)
        }
        request.end()

        assertEquals(1, roundTripSpans().size)
    }

    /**
     * Begin a transaction, send one query in it, and end it as [end] does.
     */
    private fun inTransaction(end: (Connection) -> Publisher<Void>): Mono<Void> = Mono.usingWhen(
        connectionFactory.create(),
        { connection ->
            Mono.from(connection.beginTransaction())
                .thenMany(connection.createStatement("SELECT 1").execute())
                .flatMap { it.rowsUpdated }
                .then(Mono.from(end(connection)))
        },
        { it.close() }
    )

    @Test
    fun `of - Names a PostgreSQL database by its host, port and name, and never its user`() {
        val database = QueriedDatabase.of(
            ConnectionFactoryOptions.parse("r2dbc:postgresql://sympauthy:secret@db.internal:5433/accounts")
        )

        assertEquals("postgresql", database.systemName)
        assertEquals("accounts", database.namespace)
        assertEquals("db.internal", database.serverAddress)
        assertEquals(5433, database.serverPort)
    }

    @Test
    fun `of - Names an H2 database by the last segment of its path, and gives it no server`() {
        val file = QueriedDatabase.of(ConnectionFactoryOptions.parse("r2dbc:h2:file://localhost/./sympauthy"))
        val memory = QueriedDatabase.of(
            ConnectionFactoryOptions.parse("r2dbc:h2:mem:///sympauthy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
        )

        assertEquals("sympauthy", file.namespace)
        assertNull(file.serverAddress)
        assertNull(file.serverPort)
        assertEquals("sympauthy", memory.namespace)
    }

    @Test
    fun `of - Reads a port the datasource's options gave as a string`() {
        val database = QueriedDatabase.of(
            ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(HOST, "db.internal")
                .option(Option.valueOf<String>("port"), "5433")
                .build()
        )

        assertEquals(5433, database.serverPort)
    }
}
