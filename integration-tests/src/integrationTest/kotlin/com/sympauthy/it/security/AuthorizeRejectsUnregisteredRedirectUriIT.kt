package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the authorization endpoint must not honour a `redirect_uri` that is not
 * registered for the client: it must neither redirect the user-agent to that URI nor deliver an
 * authorization code to it.**
 *
 * Risk: this is the classic open-redirect / authorization-code-exfiltration vector. If the server
 * redirected to (or issued a code toward) an arbitrary attacker-supplied `redirect_uri`, an attacker
 * could steal authorization codes. OAuth 2.1 §7.5.3 and the OAuth 2.0 Security BCP require exact-match
 * validation of `redirect_uri` against the client's registered set; an unregistered value turns the
 * request into a failed attempt that lands on the flow's own error page, carrying no code.
 *
 * This sends an authorize request whose `redirect_uri` is an unregistered attacker URL and asserts the
 * server never redirects to that URL and never emits a `code`, on each supported database.
 *
 * Source: [OAuth 2.1 §7.5.3 (Redirection Endpoint)](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-7.5.3)
 * and the [OAuth 2.0 Security BCP](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics).
 */
@Tag("security")
class AuthorizeRejectsUnregisteredRedirectUriIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "authorize refuses an unregistered redirect_uri and issues no code on {0}")
    @EnumSource(Database::class)
    fun authorizeRejectsUnregisteredRedirectUri(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val attackerRedirectUri = "https://attacker.example/callback"

            val response = httpGet(
                authorizeUrl(sympauthy, registry, mapOf("redirect_uri" to attackerRedirectUri)),
                followRedirects = false,
            )

            val location = response.headers().firstValue("Location").orElse(null)
            assertFalse(
                location != null && location.startsWith(attackerRedirectUri),
                "authorize must never redirect to an unregistered redirect_uri, Location was: $location",
            )
            assertFalse(
                location != null && location.contains("code="),
                "authorize must not issue an authorization code for an unregistered redirect_uri, Location was: $location",
            )
        }
    }
}
