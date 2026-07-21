package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the token endpoint must reject an authorization-code exchange whose
 * `redirect_uri` differs from the one bound to the code at authorization time, with `invalid_grant`
 * (HTTP 400).**
 *
 * Risk: RFC 6749 §4.1.3 requires the `redirect_uri` presented at the token endpoint to be identical to
 * the one used in the authorization request. Skipping that check would let an attacker who obtained a
 * code redeem it against a different (their own) `redirect_uri`, undermining the binding between a code
 * and the endpoint it was issued for.
 *
 * This drives a real sign-up to obtain a genuine authorization code, then exchanges it with a
 * different `redirect_uri` and asserts a 400 `invalid_grant`, on each supported database.
 *
 * Source: [RFC 6749 §4.1.3 (Access Token Request)](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.3).
 */
@Tag("security")
class TokenRedirectUriMismatchIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "token endpoint rejects a mismatched redirect_uri with invalid_grant on {0}")
    @EnumSource(Database::class)
    fun tokenEndpointRejectsMismatchedRedirectUri(database: Database) {
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
                    // A different redirect_uri than the one bound to the code at authorization time.
                    "redirect_uri" to "https://attacker.example/callback",
                    "client_id" to registry.clientId(),
                    "code_verifier" to generatePkce().verifier,
                ),
            )

            assertEquals(400, response.statusCode(), "mismatched redirect_uri must be rejected, body=${response.body()}")
            assertTrue(
                response.body().contains("invalid_grant"),
                "expected an invalid_grant error, was: ${response.body()}",
            )
        }
    }
}
