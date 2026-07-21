package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the token endpoint must reject an authorization code it never issued with
 * `invalid_grant` (HTTP 400), not mint tokens.**
 *
 * Risk: the authorization-code grant is the linchpin of the flow. If the token endpoint accepted (or
 * leaked information about) codes it did not issue, an attacker could forge or brute-force codes to
 * obtain tokens without ever authenticating a user. Per RFC 6749 an unknown/expired/revoked code must
 * be answered with `invalid_grant`.
 *
 * This posts a made-up code to the token endpoint of the running native image and asserts a 400
 * `invalid_grant`, on each supported database.
 *
 * Source: [RFC 6749 §5.2 (invalid_grant)](https://datatracker.ietf.org/doc/html/rfc6749#section-5.2)
 * and [§10.5 (authorization code security)](https://datatracker.ietf.org/doc/html/rfc6749#section-10.5).
 */
@Tag("security")
class TokenEndpointRejectsUnknownCodeIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "token endpoint rejects an unknown code with invalid_grant on {0}")
    @EnumSource(Database::class)
    fun tokenEndpointRejectsUnknownCodeWithInvalidGrant(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val response = httpPostForm(
                discovery(sympauthy)["token_endpoint"] as String,
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to "this-code-was-never-issued",
                    "redirect_uri" to registry.redirectUri(),
                    "client_id" to registry.clientId(),
                    "code_verifier" to generatePkce().verifier,
                ),
            )

            assertEquals(400, response.statusCode(), "unknown code must be rejected, body=${response.body()}")
            assertTrue(
                response.body().contains("invalid_grant"),
                "expected an invalid_grant error, was: ${response.body()}",
            )
        }
    }
}
