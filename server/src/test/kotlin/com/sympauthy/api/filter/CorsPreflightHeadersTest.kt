package com.sympauthy.api.filter

import com.sympauthy.api.filter.CorsPreflightHeaders.Companion.mergeAllowedHeaders
import com.sympauthy.config.model.DisabledCorsConfig
import com.sympauthy.config.model.EnabledCorsConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CorsPreflightHeadersTest {

    @Test
    fun `Mandatory headers are advertised when nothing is configured`() {
        assertEquals("Content-Type, Authorization, DPoP", mergeAllowedHeaders(emptyList()))
    }

    @Test
    fun `Configured headers are appended after the mandatory ones`() {
        assertEquals(
            "Content-Type, Authorization, DPoP, X-Requested-With, X-Trace-Id",
            mergeAllowedHeaders(listOf("X-Requested-With", "X-Trace-Id"))
        )
    }

    @Test
    fun `A configured header duplicating a mandatory one keeps the mandatory casing and position`() {
        assertEquals(
            "Content-Type, Authorization, DPoP, X-Foo",
            mergeAllowedHeaders(listOf("content-type", "X-Foo"))
        )
    }

    @Test
    fun `Configured headers differing only by case are deduplicated`() {
        assertEquals(
            "Content-Type, Authorization, DPoP, X-Foo",
            mergeAllowedHeaders(listOf("X-Foo", "x-foo"))
        )
    }

    @Test
    fun `Blank configured headers are skipped`() {
        assertEquals(
            "Content-Type, Authorization, DPoP, X-Foo",
            mergeAllowedHeaders(listOf("   ", "X-Foo"))
        )
    }

    @Test
    fun `Enabled config exposes the merged value`() {
        val headers = CorsPreflightHeaders(EnabledCorsConfig(listOf("X-Requested-With")))

        assertEquals("Content-Type, Authorization, DPoP, X-Requested-With", headers.allowedHeaders)
    }

    @Test
    fun `Invalid config falls back to the mandatory headers rather than breaking CORS`() {
        val headers = CorsPreflightHeaders(DisabledCorsConfig(emptyList()))

        assertEquals("Content-Type, Authorization, DPoP", headers.allowedHeaders)
    }
}
