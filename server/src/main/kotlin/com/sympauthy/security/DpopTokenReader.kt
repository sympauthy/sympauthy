package com.sympauthy.security

import io.micronaut.http.HttpHeaders.AUTHORIZATION
import io.micronaut.http.HttpRequest
import io.micronaut.security.token.reader.HttpHeaderTokenReader
import jakarta.inject.Singleton

/**
 * Reads an access token presented as `Authorization: DPoP <token>`, the scheme RFC 9449 §7.1 defines for a
 * token bound to a key. The framework's own reader matches the `Bearer` scheme alone, so without this one a
 * bound token presented the way its binding requires would read as no credential at all.
 */
@Singleton
class DpopTokenReader : HttpHeaderTokenReader() {

    override fun getHeaderName(): String = AUTHORIZATION

    override fun getPrefix(): String = "DPoP"

    /**
     * Whether [request] presents [token] under the `DPoP` scheme, rather than as a bearer token or in a
     * cookie.
     */
    fun isPresentedWith(request: HttpRequest<*>, token: String): Boolean {
        return findToken(request).orElse(null) == token
    }
}
