package com.sympauthy.monitoring.telemetry

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.tracing.opentelemetry.instrument.http.client.MicronautHttpClientTelemetryFactory
import io.micronaut.tracing.opentelemetry.instrument.http.server.MicronautHttpServerTelemetryFactory
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.semconv.UrlAttributes.URL_FULL
import io.opentelemetry.semconv.UrlAttributes.URL_QUERY
import jakarta.inject.Singleton

/**
 * Replaces the query string of every request this server serves with [REDACTED], in the attributes its span
 * and its metrics are both recorded from.
 *
 * It is ordered last so that it reads what the framework's own extractor wrote, and it overwrites rather than
 * removes because the builder it is handed cannot remove an attribute once it is set. `docs/design/telemetry.md`
 * says why the query goes whole.
 */
@Singleton
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
@Order(Ordered.LOWEST_PRECEDENCE)
@MicronautHttpServerTelemetryFactory.Server
class QueryRedactingHttpServerAttributesExtractor : AttributesExtractor<HttpRequest<*>, HttpResponse<*>> {

    override fun onStart(attributes: AttributesBuilder, parentContext: Context, request: HttpRequest<*>) {
        redactQuery(attributes)
    }

    override fun onEnd(
        attributes: AttributesBuilder,
        context: Context,
        request: HttpRequest<*>,
        response: HttpResponse<*>?,
        error: Throwable?
    ) = Unit
}

/**
 * Replaces the query string of every request this server sends with [REDACTED], in the attributes its span
 * and its metrics are both recorded from, on the terms of [QueryRedactingHttpServerAttributesExtractor].
 */
@Singleton
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
@Order(Ordered.LOWEST_PRECEDENCE)
@MicronautHttpClientTelemetryFactory.Client
class QueryRedactingHttpClientAttributesExtractor : AttributesExtractor<MutableHttpRequest<*>, HttpResponse<*>> {

    override fun onStart(attributes: AttributesBuilder, parentContext: Context, request: MutableHttpRequest<*>) {
        redactQuery(attributes)
    }

    override fun onEnd(
        attributes: AttributesBuilder,
        context: Context,
        request: MutableHttpRequest<*>,
        response: HttpResponse<*>?,
        error: Throwable?
    ) = Unit
}

/**
 * What a query string is replaced with: the value OpenTelemetry's semantic conventions give a redacted one.
 */
const val REDACTED = "REDACTED"

/**
 * Replace the query in [attributes]' `url.query` with [REDACTED], and the query in their `url.full` too,
 * leaving each attribute absent where it was.
 *
 * A fragment is dropped from `url.full`: it is never sent, and a token is what one carries back from an
 * implicit grant.
 */
internal fun redactQuery(attributes: AttributesBuilder) {
    val built = attributes.build()
    if (built.get(URL_QUERY) != null) {
        attributes.put(URL_QUERY, REDACTED)
    }
    val fullUrl = built.get(URL_FULL) ?: return
    val end = fullUrl.indexOfAny(charArrayOf('?', '#'))
    if (end != -1) {
        val query = if (fullUrl[end] == '?') "?$REDACTED" else ""
        attributes.put(URL_FULL, fullUrl.substring(0, end) + query)
    }
}
