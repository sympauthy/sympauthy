package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
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
            val code = signUpForCode(registry)

            val response = exchangeCode(sympauthy, registry, code, mapOf("code_verifier" to null))

            assertOAuthError(response, 400, "invalid_grant", "a missing verifier must be rejected")
        }
    }
}
