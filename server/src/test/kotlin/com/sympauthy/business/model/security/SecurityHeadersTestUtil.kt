package com.sympauthy.business.model.security

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.simple.SimpleHttpHeaders

/**
 * The headers of one request, built as the framework builds them so that a case about which header
 * is believed is not also a case about how a header is looked up.
 *
 * Repeating a name adds a second value under it, which is what a chain of proxies each appending to
 * `X-Forwarded-For` produces.
 */
fun headersOf(vararg values: Pair<String, String>) = SimpleHttpHeaders(ConversionService.SHARED)
    .also { headers -> values.forEach { (name, value) -> headers.add(name, value) } }
