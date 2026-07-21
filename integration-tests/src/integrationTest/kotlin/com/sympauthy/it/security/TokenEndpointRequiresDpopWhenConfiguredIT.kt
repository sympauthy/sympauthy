package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **when DPoP is required by configuration, the token endpoint must refuse a token
 * request that carries no DPoP proof, with `invalid_dpop_proof` (HTTP 400).**
 *
 * Risk: DPoP (RFC 9449) sender-constrains tokens to the client's key, so a stolen bearer token cannot
 * be replayed. If the server issued plain bearer tokens when `dpop-required` is on, that protection
 * would silently disappear. Enforcement happens before grant processing, so even an otherwise-valid
 * authorization-code exchange must be rejected without a proof.
 *
 * This obtains a genuine authorization code, then exchanges it with **no** `DPoP` header against a
 * server configured with `dpop-required: true`, asserting a 400 `invalid_dpop_proof`, on each supported
 * database.
 *
 * Source: [RFC 9449 §5 (Token Request) / §7](https://datatracker.ietf.org/doc/html/rfc9449#section-7).
 */
@Tag("security")
class TokenEndpointRequiresDpopWhenConfiguredIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "token request without DPoP is refused when dpop-required on {0}")
    @EnumSource(Database::class)
    fun tokenEndpointRequiresDpopWhenConfigured(database: Database) {
        val dpopRequired = mapOf("auth" to mapOf("token" to mapOf("dpop-required" to true)))

        withContainer(database, dpopRequired) { sympauthy, registry ->
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
                    "code_verifier" to generatePkce().verifier,
                    // Deliberately no DPoP header.
                ),
            )

            assertEquals(400, response.statusCode(), "a token request without DPoP must be refused, body=${response.body()}")
            assertTrue(
                response.body().contains("invalid_dpop_proof"),
                "expected an invalid_dpop_proof error, was: ${response.body()}",
            )
        }
    }
}
