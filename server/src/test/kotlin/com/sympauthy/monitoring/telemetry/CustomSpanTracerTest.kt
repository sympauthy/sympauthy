package com.sympauthy.monitoring.telemetry

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.EmptyCoroutineContext

class CustomSpanTracerTest {

    private val exported = CopyOnWriteArrayList<SpanData>()

    private val exporter = object : SpanExporter {
        override fun export(spans: Collection<SpanData>): CompletableResultCode {
            exported.addAll(spans)
            return CompletableResultCode.ofSuccess()
        }

        override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

        override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }

    private val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()

    private val openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()

    private val customSpanTracer = CustomSpanTracer(openTelemetry)

    @AfterEach
    fun tearDown() {
        tracerProvider.close()
    }

    /**
     * Run [block] as a request is served: under a server span the framework's instrumenter opened, carried on the
     * propagated context of the coroutine. Return that span once ended.
     */
    private fun underRequest(
        sdk: OpenTelemetrySdk = openTelemetry,
        block: suspend () -> Unit
    ): SpanData = runTest {
        val instrumenter = Instrumenter.builder<String, Void>(sdk, "test") { it }
            .buildInstrumenter(SpanKindExtractor.alwaysServer())
        val context = instrumenter.start(Context.root(), "GET /request")
        val propagated = PropagatedContext.empty().plus(OpenTelemetryPropagationContext(context))
        withContext(KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, propagated)) {
            block()
        }
        instrumenter.end(context, "GET /request", null, null)
    }.let { exported.single { it.name == "GET /request" } }

    private fun span(name: String): SpanData = exported.single { it.name == name }

    @Test
    fun `inSpan - Opens a span under the span of the request, and the next one under it`() {
        val request = underRequest {
            customSpanTracer.advanceFlow {
                customSpanTracer.resolvePurpose("oauth2_authorize") {}
            }
        }

        val advance = span(CustomSpanTracer.ADVANCE_FLOW)
        val resolve = span("resolve oauth2_authorize")
        assertEquals(request.spanContext.spanId, advance.parentSpanId)
        assertEquals(advance.spanContext.spanId, resolve.parentSpanId)
        assertEquals("oauth2_authorize", resolve.attributes.get(AttributeKey.stringKey("purpose")))
    }

    @Test
    fun `inSpan - Runs the block and opens no span outside any request`() = runTest {
        val result = customSpanTracer.hashPassword("verify") { "hashed" }

        assertEquals("hashed", result)
        assertTrue(exported.isEmpty())
    }

    @Test
    fun `inSpan - Opens no span under a request that is not recorded`() {
        val unsampled = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .setSampler(Sampler.alwaysOff())
            .build()
        val sdk = OpenTelemetrySdk.builder().setTracerProvider(unsampled).build()
        var ran = false

        runTest {
            val instrumenter = Instrumenter.builder<String, Void>(sdk, "test") { it }
                .buildInstrumenter(SpanKindExtractor.alwaysServer())
            val context = instrumenter.start(Context.root(), "GET /request")
            val propagated = PropagatedContext.empty().plus(OpenTelemetryPropagationContext(context))
            withContext(KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, propagated)) {
                CustomSpanTracer(sdk).completeFlow {
                    ran = true
                    assertTrue(!Span.current().isRecording)
                }
            }
            instrumenter.end(context, "GET /request", null, null)
        }
        unsampled.close()

        assertTrue(ran)
        assertTrue(exported.isEmpty())
    }

    @Test
    fun `inSpan - Ends as an error a stage that threw, and rethrows`() {
        underRequest {
            assertThrows<IllegalStateException> {
                customSpanTracer.issueToken(CustomSpanTracer.ID_TOKEN) { error("No key.") }
            }
        }

        assertEquals(StatusCode.ERROR, span("issue id_token").status.statusCode)
    }

    @Test
    fun `inSpan - Leaves a refusal the stage said it ended with to its outcome, rather than ending it as an error`() {
        underRequest {
            assertThrows<IllegalArgumentException> {
                customSpanTracer.validateAccessToken("bearer") { span ->
                    span.setOutcome(Outcome.Refused("invalid_token"))
                    throw IllegalArgumentException("Expired.")
                }
            }
        }

        val validation = span(CustomSpanTracer.VALIDATE_ACCESS_TOKEN)
        assertEquals(StatusCode.UNSET, validation.status.statusCode)
        assertEquals("refused", validation.attributes.get(OUTCOME))
    }

    @Test
    fun `validateAccessToken - Carries the scheme, how the check ended, and the client once found`() {
        underRequest {
            customSpanTracer.validateAccessToken("dpop") { span ->
                span.setOutcome(Outcome.Refused("invalid_token"))
                span.setClientId("client")
            }
        }

        val validation = span(CustomSpanTracer.VALIDATE_ACCESS_TOKEN).attributes
        assertEquals("dpop", validation.get(AttributeKey.stringKey("scheme")))
        assertEquals("refused", validation.get(OUTCOME))
        assertEquals("invalid_token", validation.get(ERROR_TYPE))
        assertEquals("client", validation.get(CLIENT_ID))
    }

    @Test
    fun `markFlowSession - Marks the request's own span, and links it to the span that started the session`() {
        val sessionId = UUID.randomUUID()
        val startedBy = SpanReference("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331")

        val request = underRequest {
            customSpanTracer.advanceFlow {
                customSpanTracer.markFlowSession(sessionId, startedBy)
            }
        }

        assertEquals(sessionId.toString(), request.attributes.get(CustomSpanTracer.SESSION_ID))
        assertNull(span(CustomSpanTracer.ADVANCE_FLOW).attributes.get(CustomSpanTracer.SESSION_ID))
        val link = request.links.single().spanContext
        assertEquals(startedBy.traceId, link.traceId)
        assertEquals(startedBy.spanId, link.spanId)
    }

    @Test
    fun `markFlowSession - Links no request to itself`() {
        val sessionId = UUID.randomUUID()

        val request = underRequest {
            val startedBy = customSpanTracer.requestSpanReferenceOrNull()
            customSpanTracer.markFlowSession(sessionId, startedBy)
        }

        assertEquals(sessionId.toString(), request.attributes.get(CustomSpanTracer.SESSION_ID))
        assertTrue(request.links.isEmpty())
    }

    @Test
    fun `requestSpanReferenceOrNull - References the request's own span from inside a stage`() {
        var reference: SpanReference? = null

        val request = underRequest {
            customSpanTracer.advanceFlow {
                reference = customSpanTracer.requestSpanReferenceOrNull()
            }
        }

        assertEquals(SpanReference.of(request.spanContext), reference)
    }

    @Test
    fun `requestSpanReferenceOrNull - References nothing outside any request`() = runTest {
        assertNull(customSpanTracer.requestSpanReferenceOrNull())
    }
}
