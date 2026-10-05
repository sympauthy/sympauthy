package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **an authorization code issued to one client must not be redeemable by a
 * different client, even a validly registered one; the token endpoint answers `invalid_grant`
 * (HTTP 400).**
 *
 * Risk: RFC 6749 §4.1.3 / §10.5 bind an authorization code to the client it was issued to. If any
 * client could redeem any code, a malicious-but-registered client that intercepts another client's code
 * could exchange it for that user's tokens. The token endpoint checks the code's owning client against
 * the authenticated client (`token.mismatching_client`) and refuses the exchange.
 *
 * This drives a real sign-up for client `test-app`, then attempts to redeem the resulting code as a
 * second registered public client and asserts a 400 `invalid_grant`, on each supported database.
 *
 * Source: [RFC 6749 §4.1.3](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.3) and
 * [§10.5](https://datatracker.ietf.org/doc/html/rfc6749#section-10.5).
 */
@Tag("security")
class AuthorizationCodeBoundToClientIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a code issued to one client cannot be redeemed by another on {0}")
    @EnumSource(Database::class)
    fun authorizationCodeIsBoundToIssuingClient(database: Database) {
        val secondClient = mapOf(
            "clients" to mapOf(
                OTHER_CLIENT_ID to mapOf(
                    "public" to true,
                    "allowed-grant-types" to listOf("authorization_code"),
                    "allowed-scopes" to listOf("openid"),
                    "default-scopes" to listOf("openid"),
                    "allowed-redirect-uris" to listOf("https://other.example/callback"),
                ),
            ),
        )

        withContainer(database, secondClient) { sympauthy, registry ->
            val code = signUpForCode(registry)

            val response = exchangeCode(sympauthy, registry, code, mapOf("client_id" to OTHER_CLIENT_ID))

            assertOAuthError(response, 400, "invalid_grant", "a foreign client must not redeem the code")
        }
    }

    private companion object {
        const val OTHER_CLIENT_ID = "other-client"
    }
}
