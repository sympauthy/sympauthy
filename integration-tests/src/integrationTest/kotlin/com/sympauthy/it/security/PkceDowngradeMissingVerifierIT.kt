package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **when the authorization code was issued against a `code_challenge`, the token
 * endpoint must reject an exchange that omits `code_verifier`, with `invalid_grant` (HTTP 400).**
 *
 * Risk: this is the PKCE-downgrade attack. An attacker who steals an authorization code could try to
 * redeem it while simply dropping the `code_verifier`, hoping the server skips the PKCE check when no
 * verifier is present. If it did, PKCE would be trivially bypassable. Per RFC 7636 §4.6 a verifier is
 * mandatory whenever a challenge was recorded, and a missing verifier must yield `invalid_grant`.
 *
 * This drives a real sign-up (which sends a challenge) to obtain a genuine authorization code, then
 * exchanges it with **no** `code_verifier` and asserts a 400 `invalid_grant`, on each supported
 * database.
 *
 * Source: [RFC 7636 §4.6 (Server verifies code_verifier)](https://datatracker.ietf.org/doc/html/rfc7636#section-4.6).
 */
@Tag("security")
class PkceDowngradeMissingVerifierIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "token endpoint rejects a missing code_verifier with invalid_grant on {0}")
    @EnumSource(Database::class)
    fun tokenEndpointRejectsMissingVerifier(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val result = registry.newFlow()
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }
                .run()
            val code = checkNotNull(result.code()) { "expected an authorization code from sign-up" }

            val response = httpPostForm(
                discovery(sympauthy)["token_endpoint"] as String,
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to registry.redirectUri(),
                    "client_id" to registry.clientId(),
                    // No code_verifier at all — the client sent a challenge, so one is mandatory.
                ),
            )

            assertEquals(400, response.statusCode(), "missing verifier must be rejected, body=${response.body()}")
            assertTrue(
                response.body().contains("invalid_grant"),
                "expected an invalid_grant error, was: ${response.body()}",
            )
        }
    }
}
