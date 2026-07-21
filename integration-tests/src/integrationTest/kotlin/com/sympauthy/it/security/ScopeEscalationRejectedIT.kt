package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the authorization endpoint must refuse a request for a scope outside the
 * client's allowed set and must not issue an authorization code.**
 *
 * Risk: RFC 6749 §3.3 — a client must obtain only the scopes it is authorized for. If the server
 * honoured (or issued a code for) a scope the client is not allowed to request, a client could escalate
 * its privileges beyond its configuration. The base test client allows only `openid`; requesting the
 * standard `profile` scope (which exists on the server but is not in the client's allowed set) makes
 * the authorization a failed attempt that lands on the flow error page, carrying no code.
 *
 * This sends an authorize request for `openid profile` against a client allowed only `openid`, and
 * asserts no authorization code is issued, on each supported database.
 *
 * Source: [RFC 6749 §3.3 (Access Token Scope)](https://datatracker.ietf.org/doc/html/rfc6749#section-3.3).
 */
@Tag("security")
class ScopeEscalationRejectedIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "authorize refuses a scope outside the client's allowed set on {0}")
    @EnumSource(Database::class)
    fun authorizeRejectsScopeOutsideAllowedSet(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val response = httpGet(
                // `profile` is a valid server scope but is NOT in this client's allowed-scopes (openid only).
                authorizeUrl(sympauthy, registry, mapOf("scope" to "openid profile")),
                followRedirects = false,
            )

            val location = response.headers().firstValue("Location").orElse(null)
            assertFalse(
                location != null && location.contains("code="),
                "authorize must not issue a code for a scope outside the client's allowed set, Location was: $location",
            )
        }
    }
}
