package com.sympauthy.business.manager.provider.openidconnect

import com.nimbusds.jose.JWSAlgorithm
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.RemoteCallMonitor
import com.sympauthy.monitoring.telemetry.LibraryHttpCallTracer
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE
import io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE
import io.opentelemetry.semconv.UrlAttributes.URL_FULL
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.EmptyCoroutineContext

class ProviderIdTokenManagerTest {

    private val conditionMonitor = ConditionMonitor()

    private val manager = ProviderIdTokenManager(
        RemoteCallMonitor(conditionMonitor, OpenTelemetry.noop()),
        LibraryHttpCallTracer(OpenTelemetry.noop())
    )

    /**
     * The configuration of a provider whose key set is at [jwksUri].
     */
    private fun configWithKeySetAt(jwksUri: URI) = ProviderOpenIdConnectConfig(
        clientId = "client",
        clientSecret = "secret",
        scopes = emptyList(),
        issuer = URI("https://issuer.example.com"),
        authorizationUri = URI("https://issuer.example.com/authorize"),
        tokenUri = URI("https://issuer.example.com/token"),
        jwksUri = jwksUri
    )

    /**
     * An id token whose signature is never checked: the key set is fetched first.
     */
    private val idToken = Base64.getUrlEncoder().withoutPadding().let { encoder ->
        listOf("""{"alg":"RS256","kid":"key"}""", """{"sub":"someone"}""", "signature")
            .joinToString(".") { encoder.encodeToString(it.toByteArray()) }
    }

    @Test
    fun `validateAndExtractClaims - Reports a key set that cannot be fetched to the provider's condition`() =
        runTest {
            // Nothing listens on port 1.
            val config = configWithKeySetAt(URI("http://127.0.0.1:1/jwks"))

            assertThrows<BusinessException> {
                manager.validateAndExtractClaims("example", config, idToken, accessToken, null)
            }

            assertTrue(conditionMonitor.isDegraded(Condition.Provider("example")))
        }

    @Test
    fun `validateAndExtractClaims - Traces the fetch of the key set under the request, with the status answered`() =
        runTest {
            val exported = CopyOnWriteArrayList<SpanData>()
            val tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(object : SpanExporter {
                    override fun export(spans: Collection<SpanData>): CompletableResultCode {
                        exported.addAll(spans)
                        return CompletableResultCode.ofSuccess()
                    }

                    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

                    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
                }))
                .build()
            val sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
            val tracedManager = ProviderIdTokenManager(
                RemoteCallMonitor(ConditionMonitor(), OpenTelemetry.noop()),
                LibraryHttpCallTracer(sdk)
            )
            val server = MockWebServer()
            repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }
            server.start()
            val config = configWithKeySetAt(server.url("/jwks?tenant=a").toUri())
            val request = sdk.getTracer("test").spanBuilder("GET /callback").startSpan()
            val propagated = PropagatedContext.empty()
                .plus(OpenTelemetryPropagationContext(Context.root().with(request)))

            withContext(KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, propagated)) {
                assertThrows<BusinessException> {
                    tracedManager.validateAndExtractClaims("example", config, idToken, accessToken, null)
                }
            }
            request.end()
            server.close()
            tracerProvider.close()

            val fetch = exported.first { it.kind == SpanKind.CLIENT }
            assertEquals("GET", fetch.name)
            assertEquals(request.spanContext.spanId, fetch.parentSpanId)
            assertEquals(503L, fetch.attributes.get(HTTP_RESPONSE_STATUS_CODE))
            assertEquals("503", fetch.attributes.get(ERROR_TYPE))
            assertEquals(server.url("/jwks").toString() + "?REDACTED", fetch.attributes.get(URL_FULL))
        }

    /** OpenID Connect Core 1.0, appendix A.3. */
    private val accessToken = "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"
    private val atHash = "77QmUPtjPfzWtF2AnpK9RQ"

    @Test
    fun `validateAtHash - Accept the hash of the access token from the same response`() {
        assertDoesNotThrow {
            manager.validateAtHash(atHash, accessToken, JWSAlgorithm.RS256, "provider")
        }
    }

    @Test
    fun `validateAtHash - Refuse the hash of another access token`() {
        val exception = assertThrows<BusinessException> {
            manager.validateAtHash(atHash, "another-access-token", JWSAlgorithm.RS256, "provider")
        }

        assertEquals("provider.openid_connect.invalid_at_hash", exception.detailsId)
    }

    @Test
    fun `validateAtHash - Leave the claim unchecked under an algorithm naming no digest`() {
        assertDoesNotThrow {
            manager.validateAtHash(atHash, "another-access-token", JWSAlgorithm.parse("EdDSA"), "provider")
        }
    }
}
