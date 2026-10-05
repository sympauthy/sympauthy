package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the token endpoint must reject a `code_verifier` that does not match the
 * `code_challenge` bound to the authorization code, with `invalid_grant` (HTTP 400).**
 *
 * Risk: PKCE is what protects a public client's authorization code from being redeemed by an attacker
 * who intercepts it. If the server accepted a `code_verifier` that does not hash to the
 * `code_challenge` supplied at authorization time, PKCE would be defeated and a stolen code could be
 * exchanged for tokens. Per RFC 7636 §4.6 the transformed verifier must equal the stored challenge, or
 * the request is answered with `invalid_grant`.
 *
 * This drives a real sign-up through the mock frontend to obtain a genuine authorization code, then
 * exchanges it with a fresh, non-matching verifier and asserts a 400 `invalid_grant`, on each
 * supported database.
 *
 * Source: [RFC 7636 §4.6 (Server verifies code_verifier)](https://datatracker.ietf.org/doc/html/rfc7636#section-4.6).
 */
@Tag("security")
class PkceVerifierMismatchIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "token endpoint rejects a mismatched code_verifier with invalid_grant on {0}")
    @EnumSource(Database::class)
    fun tokenEndpointRejectsMismatchedVerifier(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val code = signUpForCode(registry)

            // Named rather than left to the default: that this verifier cannot hash to the challenge the
            // code carries is what the refusal below turns on.
            val response = exchangeCode(
                sympauthy, registry, code,
                mapOf("code_verifier" to generatePkce().verifier),
            )

            assertOAuthError(response, 400, "invalid_grant", "a mismatched verifier must be rejected")
        }
    }
}
