package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor
import jakarta.inject.Singleton
import java.net.URL

/**
 * Opens a client span by the HTTP semantic conventions for a `GET` a library makes over a connection of its own,
 * outside the HTTP client the framework traces, so that it reads like every other call the request made.
 *
 * Its query is redacted as every URL's is, after the extractor that sets it.
 */
@Singleton
class LibraryHttpCallTracer(
    openTelemetry: OpenTelemetry
) {

    private val instrumenter: Instrumenter<URL, Int> =
        Instrumenter.builder<URL, Int>(openTelemetry, INSTRUMENTATION_SCOPE, HttpSpanNameExtractor.create(Getter))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(Getter))
            .addAttributesExtractor(QueryRedactingExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(Getter))
            .buildInstrumenter(SpanKindExtractor.alwaysClient())

    /**
     * Run [fetch] as a `GET` of [url] under the span the calling thread runs under, and return what it returns.
     * [statusOf] is read once [fetch] has returned or thrown, for the status the server answered with, or null
     * where it answered none.
     *
     * No span opens where the thread runs under none. What [fetch] throws is rethrown.
     */
    fun <T> get(url: URL, statusOf: () -> Int?, fetch: () -> T): T {
        val parent = Context.current()
        if (!Span.fromContext(parent).spanContext.isValid || !instrumenter.shouldStart(parent, url)) {
            return fetch()
        }
        val context = instrumenter.start(parent, url)
        val result = try {
            context.makeCurrent().use { fetch() }
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            instrumenter.end(context, url, statusOf(), failure)
            throw failure
        }
        instrumenter.end(context, url, statusOf(), null)
        return result
    }

    private object Getter : HttpClientAttributesGetter<URL, Int> {

        override fun getUrlFull(request: URL): String = request.toString()

        override fun getServerAddress(request: URL): String? = request.host

        override fun getServerPort(request: URL): Int = if (request.port != -1) request.port else request.defaultPort

        override fun getHttpRequestMethod(request: URL): String = GET

        override fun getHttpRequestHeader(request: URL, name: String): List<String> = emptyList()

        override fun getHttpResponseStatusCode(request: URL, response: Int?, error: Throwable?): Int? = response

        override fun getHttpResponseHeader(request: URL, response: Int?, name: String): List<String> = emptyList()
    }

    private object QueryRedactingExtractor : AttributesExtractor<URL, Int> {

        override fun onStart(attributes: AttributesBuilder, parentContext: Context, request: URL) {
            redactQuery(attributes)
        }

        override fun onEnd(
            attributes: AttributesBuilder,
            context: Context,
            request: URL,
            response: Int?,
            error: Throwable?
        ) = Unit
    }

    private companion object {
        const val INSTRUMENTATION_SCOPE = "com.sympauthy.http_client"
        const val GET = "GET"
    }
}
