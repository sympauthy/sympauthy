package com.sympauthy.it.security

import com.nimbusds.jose.util.JSONObjectUtils
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **revocation actually takes effect: a token that introspects as active must
 * introspect as inactive once revoked.**
 *
 * Risk: RFC 7009 is only meaningful if revocation is enforced. If a revoked token kept working, a
 * leaked or compromised token could not be contained. This exercises the full round-trip on the running
 * native image — issue, confirm active, revoke, confirm inactive — proving the revocation state is
 * honoured by introspection (RFC 7662).
 *
 * A confidential client obtains an access token via `client_credentials`, introspects it (active),
 * revokes it, then introspects again (inactive), on each supported database.
 *
 * Source: [RFC 7009 (Token Revocation)](https://datatracker.ietf.org/doc/html/rfc7009) and
 * [RFC 7662 (Token Introspection)](https://datatracker.ietf.org/doc/html/rfc7662).
 */
@Tag("security")
class RevokedTokenBecomesInactiveIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a revoked token introspects as inactive on {0}")
    @EnumSource(Database::class)
    fun revokedTokenIntrospectsAsInactive(database: Database) {
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
            val discovery = discovery(sympauthy)
            val tokenEndpoint = discovery["token_endpoint"] as String
            val introspectionEndpoint = discovery["introspection_endpoint"] as String
            val revocationEndpoint = discovery["revocation_endpoint"] as String
            val auth = mapOf("Authorization" to basicAuth(CLIENT_ID, CLIENT_SECRET))

            // Issue a token via client_credentials.
            val tokenResponse = httpPostForm(tokenEndpoint, mapOf("grant_type" to "client_credentials"), auth)
            assertEquals(200, tokenResponse.statusCode(), "client_credentials must issue a token, body=${tokenResponse.body()}")
            val accessToken = JSONObjectUtils.parse(tokenResponse.body())["access_token"] as String

            // It introspects as active before revocation.
            val beforeRevoke = httpPostForm(introspectionEndpoint, mapOf("token" to accessToken), auth)
            assertTrue(
                beforeRevoke.body().contains("\"active\":true"),
                "the freshly issued token should introspect as active, body=${beforeRevoke.body()}",
            )

            // Revoke it.
            val revoke = httpPostForm(revocationEndpoint, mapOf("token" to accessToken), auth)
            assertEquals(200, revoke.statusCode(), "revocation should return 200, body=${revoke.body()}")

            // It now introspects as inactive.
            val afterRevoke = httpPostForm(introspectionEndpoint, mapOf("token" to accessToken), auth)
            assertTrue(
                afterRevoke.body().contains("\"active\":false"),
                "a revoked token must introspect as inactive, body=${afterRevoke.body()}",
            )
        }
    }

    private companion object {
        const val CLIENT_ID = "confidential-client"
        const val CLIENT_SECRET = "confidential-client-secret-value"
    }
}
