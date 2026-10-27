package com.sympauthy.monitoring.telemetry

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.mockk.mockk
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE
import io.opentelemetry.semconv.UrlAttributes.URL_FULL
import io.opentelemetry.semconv.UrlAttributes.URL_PATH
import io.opentelemetry.semconv.UrlAttributes.URL_QUERY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QueryRedactingAttributesExtractorsTest {

    @Test
    fun `onStart - Redacts the query of a request served and keeps its route and path`() {
        val attributes = Attributes.builder()
            .put(HTTP_ROUTE, "/api/v1/flow/providers/{providerId}/callback")
            .put(URL_PATH, "/api/v1/flow/providers/discord/callback")
            .put(URL_QUERY, "code=SplxlOBeZQQYbYS6WxSbIA&state=af0ifjsldkj")

        QueryRedactingHttpServerAttributesExtractor()
            .onStart(attributes, Context.root(), mockk<HttpRequest<*>>())

        val redacted = attributes.build()
        assertEquals(REDACTED, redacted.get(URL_QUERY))
        assertEquals("/api/v1/flow/providers/{providerId}/callback", redacted.get(HTTP_ROUTE))
        assertEquals("/api/v1/flow/providers/discord/callback", redacted.get(URL_PATH))
    }

    @Test
    fun `onStart - Redacts the query from the full URL of a request sent`() {
        val attributes = Attributes.builder().put(URL_FULL, "https://idp.example.com/userinfo?access_token=secret")

        QueryRedactingHttpClientAttributesExtractor()
            .onStart(attributes, Context.root(), mockk<MutableHttpRequest<*>>())

        assertEquals("https://idp.example.com/userinfo?$REDACTED", attributes.build().get(URL_FULL))
    }

    @Test
    fun `onStart - Adds no query to a request that had none`() {
        val attributes = Attributes.builder().put(URL_FULL, "https://idp.example.com/token")

        QueryRedactingHttpClientAttributesExtractor()
            .onStart(attributes, Context.root(), mockk<MutableHttpRequest<*>>())

        val unchanged = attributes.build()
        assertEquals("https://idp.example.com/token", unchanged.get(URL_FULL))
        assertNull(unchanged.get(URL_QUERY))
    }
}
