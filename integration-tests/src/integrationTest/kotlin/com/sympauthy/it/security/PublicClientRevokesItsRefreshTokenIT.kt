package com.sympauthy.it.security

import com.nimbusds.jose.util.JSONObjectUtils
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **a public client can revoke the refresh token it was issued, sending its
 * `client_id` alone.**
 *
 * Risk: RFC 7009 §2.1 validates client credentials only "in case of a confidential client", and §5 lets a
 * public client identify itself with its `client_id`. A public client — usually a page in a browser — that
 * cannot revoke has no way to withdraw a refresh token at sign-out, and the token goes on minting access
 * tokens until it expires.
 *
 * A public client allowed the refresh grant signs a person up and exchanges the code, refreshes once to
 * show the grant serves it, revokes the refresh token it now holds with its `client_id` alone, then
 * presents that token again and asserts a 400 `invalid_grant`, on each supported database.
 *
 * Source: [RFC 7009 §2.1 (Revocation Request)](https://datatracker.ietf.org/doc/html/rfc7009#section-2.1).
 */
@Tag("security")
class PublicClientRevokesItsRefreshTokenIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a public client revokes its refresh token on {0}")
    @EnumSource(Database::class)
    fun publicClientRevokesItsRefreshToken(database: Database) {
        val refreshAllowed = mapOf(
            "auth" to mapOf("token" to mapOf("refresh-enabled" to true)),
            "clients" to mapOf(
                clientId to mapOf("allowed-grant-types" to listOf("authorization_code", "refresh_token")),
            ),
        )

        withContainer(database, refreshAllowed) { sympauthy, registry ->
            val tokens = signUpAndExchange(registry)
            val issued = checkNotNull(tokens.refreshToken()) { "the refresh grant should yield a refresh token" }

            val refreshed = refreshAsPublicClient(discovery(sympauthy).tokenEndpoint, issued, registry.clientId())
            assertEquals(200, refreshed.statusCode(), "the refresh grant should serve, body=${refreshed.body()}")
            val refreshToken = JSONObjectUtils.parse(refreshed.body())["refresh_token"] as String

            val revoke = httpPostForm(
                discovery(sympauthy).revocationEndpoint!!,
                mapOf(
                    "token" to refreshToken,
                    "token_type_hint" to "refresh_token",
                    "client_id" to registry.clientId(),
                ),
            )
            assertEquals(200, revoke.statusCode(), "a public client should be able to revoke, body=${revoke.body()}")

            val afterRevocation = refreshAsPublicClient(
                discovery(sympauthy).tokenEndpoint,
                refreshToken,
                registry.clientId(),
            )
            assertOAuthError(afterRevocation, 400, "invalid_grant", "a revoked refresh token must not be refreshed")
        }
    }

    private fun refreshAsPublicClient(tokenEndpoint: String, refreshToken: String, clientId: String) =
        httpPostForm(
            tokenEndpoint,
            mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "client_id" to clientId),
        )
}
