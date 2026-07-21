package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **introspecting a token owned by a different client must return
 * `{"active": false}` with no metadata; a client must not learn anything about another client's
 * tokens.**
 *
 * Risk: RFC 7662 scopes introspection to the token's own client. If a client could introspect tokens it
 * does not own, a malicious-but-registered client could confirm that an intercepted token is valid and
 * read its scopes, subject, and expiry. SympAuthy returns only `{"active": false}` when the
 * authenticated client is not the token's owner.
 *
 * This obtains a genuine access token for the public client `test-app`, then introspects it while
 * authenticated as a *different* confidential client and asserts an inactive, metadata-free response,
 * on each supported database.
 *
 * Source: [RFC 7662 §2.2 (Introspection Response)](https://datatracker.ietf.org/doc/html/rfc7662#section-2.2).
 */
@Tag("security")
class IntrospectionActiveFalseForOtherClientsTokenIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a client cannot introspect another client's token on {0}")
    @EnumSource(Database::class)
    fun introspectionOfForeignTokenIsInactive(database: Database) {
        val otherClient = mapOf(
            "clients" to mapOf(
                OTHER_CLIENT_ID to mapOf(
                    "public" to false,
                    "secret" to OTHER_CLIENT_SECRET,
                    "allowed-grant-types" to listOf("client_credentials"),
                ),
            ),
        )

        withContainer(database, otherClient) { sympauthy, registry ->
            // A real access token owned by the public client `test-app`.
            val tokens = registry.newFlow()
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }
                .run()
                .exchange()
            val foreignAccessToken = checkNotNull(tokens.accessToken()) { "expected an access token from exchange" }

            // Introspect it while authenticated as a *different* client.
            val response = httpPostForm(
                discovery(sympauthy)["introspection_endpoint"] as String,
                mapOf("token" to foreignAccessToken),
                headers = mapOf("Authorization" to basicAuth(OTHER_CLIENT_ID, OTHER_CLIENT_SECRET)),
            )

            assertEquals(200, response.statusCode(), "introspection should answer, body=${response.body()}")
            assertTrue(
                response.body().contains("\"active\":false"),
                "a foreign client's token must introspect as inactive, body=${response.body()}",
            )
            assertTrue(
                !response.body().contains("\"sub\"") && !response.body().contains("\"scope\""),
                "an inactive introspection response must not leak token metadata, body=${response.body()}",
            )
        }
    }

    private companion object {
        const val OTHER_CLIENT_ID = "other-client"
        const val OTHER_CLIENT_SECRET = "other-client-secret-value"
    }
}
