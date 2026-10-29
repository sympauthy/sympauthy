package com.sympauthy.monitoring.telemetry

import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE
import io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY
import io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT
import io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE
import io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME
import io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE
import io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS
import io.opentelemetry.semconv.ServerAttributes.SERVER_PORT
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.R2dbcException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CopyOnWriteArrayList

class QueryTracingTest {

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

    private val connectionFactory = QueryTracingConnectionFactoryFactory().connectionFactory(
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

    private fun querySpans(): List<SpanData> = exported.filter { it.kind == SpanKind.CLIENT }

    @Test
    fun `connectionFactory - Opens a client span for a query, under the span of the request that made it`() {
        val request = underRequest { execute("SELECT 1").block() }

        val query = querySpans().single()
        assertEquals(request.spanContext.traceId, query.traceId)
        assertEquals(request.spanContext.spanId, query.parentSpanId)
        assertEquals("h2database", query.attributes.get(DB_SYSTEM_NAME))
        assertEquals("query-tracing-test", query.attributes.get(DB_NAMESPACE))
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
    fun `connectionFactory - Records the SQL state of a query the database refused`() {
        val failure = assertThrows<R2dbcException> {
            underRequest { execute("""SELECT "nothing" FROM "no_such_table"""").block() }
        }

        val query = querySpans().single()
        assertEquals(failure.sqlState, query.attributes.get(DB_RESPONSE_STATUS_CODE))
        assertEquals(failure.sqlState, query.attributes.get(ERROR_TYPE))
    }

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
    fun `onStart - Leaves the address out of a database the options name no host for`() {
        val query = DatabaseQuery(QueriedDatabase("h2database", "sympauthy", null, null), listOf("SELECT 1"), 0)
        val attributes = Attributes.builder()

        DatabaseQueryAttributesExtractor.onStart(attributes, Context.root(), query)

        assertNull(attributes.build().get(SERVER_ADDRESS))
        assertNull(attributes.build().get(SERVER_PORT))
    }
}
