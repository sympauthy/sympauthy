package com.sympauthy.it.compliance

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Compliance scenario — **revocation actually takes effect: a token that introspects as active must
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
 * Source: [RFC 7009 §2 (Token Revocation)](https://datatracker.ietf.org/doc/html/rfc7009#section-2) and
 * [RFC 7662 §2.2 (Introspection Response)](https://datatracker.ietf.org/doc/html/rfc7662#section-2.2).
 */
@Tag("compliance")
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
            val accessToken = clientCredentialsToken(sympauthy, CLIENT_ID, CLIENT_SECRET)

            assertEquals(
                true, introspect(sympauthy, accessToken, CLIENT_ID, CLIENT_SECRET).active,
                "the freshly issued token should introspect as active",
            )

            revoke(sympauthy, accessToken, CLIENT_ID, CLIENT_SECRET)

            assertEquals(
                false, introspect(sympauthy, accessToken, CLIENT_ID, CLIENT_SECRET).active,
                "a revoked token must introspect as inactive",
            )
        }
    }

    private companion object {
        const val CLIENT_ID = "confidential-client"
        const val CLIENT_SECRET = "confidential-client-secret-value"
    }
}
