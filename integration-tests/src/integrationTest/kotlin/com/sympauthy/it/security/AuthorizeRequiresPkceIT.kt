package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the authorization endpoint must reject a request that omits PKCE
 * (`code_challenge`) and must not issue an authorization code.**
 *
 * Risk: OAuth 2.1 makes PKCE mandatory for every client. If the server issued a code without a bound
 * `code_challenge`, a public client's code would no longer be protected against interception —
 * defeating the very purpose of PKCE and re-opening the authorization-code-injection attack. A missing
 * `code_challenge` is treated as a failed authorization attempt, so no code is emitted.
 *
 * This sends an authorize request with no `code_challenge` / `code_challenge_method` and asserts that
 * no authorization `code` is issued, on each supported database.
 *
 * Source: [RFC 7636 (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636) and
 * [OAuth 2.1 (PKCE mandatory)](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-4.1.1).
 */
@Tag("security")
class AuthorizeRequiresPkceIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "authorize refuses a request without PKCE and issues no code on {0}")
    @EnumSource(Database::class)
    fun authorizeRequiresPkce(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val response = httpGet(
                authorizeUrl(
                    sympauthy,
                    registry,
                    // Drop both PKCE parameters entirely — a compliant server must refuse to issue a code.
                    mapOf("code_challenge" to null, "code_challenge_method" to null),
                ),
                followRedirects = false,
            )

            val location = response.headers().firstValue("Location").orElse(null)
            assertFalse(
                location != null && location.contains("code="),
                "authorize must not issue an authorization code when PKCE is absent, Location was: $location",
            )
        }
    }
}
