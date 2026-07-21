package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the revocation endpoint must answer `200 OK` for an authenticated client even
 * when the submitted token is unknown, malformed, or already revoked.**
 *
 * Risk: RFC 7009 §2.2 mandates a `200` regardless of token validity so that a client cannot use the
 * revocation endpoint as an oracle to distinguish "valid token" from "unknown token" by status code —
 * which would leak whether an intercepted token exists. The response must be indistinguishable for a
 * real and a bogus token.
 *
 * This authenticates a confidential client and posts a garbage token to the revocation endpoint,
 * asserting a `200`, on each supported database.
 *
 * Source: [RFC 7009 §2.2 (Revocation Response)](https://datatracker.ietf.org/doc/html/rfc7009#section-2.2).
 */
@Tag("security")
class RevokeAlwaysReturns200IT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "revocation of an unknown token still returns 200 on {0}")
    @EnumSource(Database::class)
    fun revokingAnUnknownTokenReturns200(database: Database) {
        val confidentialClient = mapOf(
            "clients" to mapOf(
                CLIENT_ID to mapOf(
                    "public" to false,
                    "secret" to CLIENT_SECRET,
                    "allowed-grant-types" to listOf("client_credentials"),
                ),
            ),
        )

        withContainer(database, confidentialClient) { sympauthy, _ ->
            val response = httpPostForm(
                discovery(sympauthy)["revocation_endpoint"] as String,
                mapOf("token" to "this-token-does-not-exist"),
                headers = mapOf("Authorization" to basicAuth(CLIENT_ID, CLIENT_SECRET)),
            )

            assertEquals(
                200,
                response.statusCode(),
                "revocation of an unknown token must still return 200 (RFC 7009), body=${response.body()}",
            )
        }
    }

    private companion object {
        const val CLIENT_ID = "confidential-client"
        const val CLIENT_SECRET = "confidential-client-secret-value"
    }
}
