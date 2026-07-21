package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the introspection endpoint must not reveal token metadata to an unauthenticated
 * caller: without client credentials the request is rejected, never answered with token details.**
 *
 * Risk: RFC 7662 §2.1 requires the protected-resource caller to authenticate. If introspection
 * accepted anonymous requests, anyone could probe whether an intercepted token is active and read its
 * scopes, subject, and expiry. SympAuthy resolves the client via credentials (Basic Auth or
 * `client_id`/`client_secret`) and rejects a request that carries none.
 *
 * This posts an introspection request with **no** client credentials and asserts it is rejected (not a
 * `200` with token metadata), on each supported database.
 *
 * Source: [RFC 7662 §2.1 (Introspection Request)](https://datatracker.ietf.org/doc/html/rfc7662#section-2.1).
 */
@Tag("security")
class IntrospectionRequiresClientAuthIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "introspection without client credentials is rejected on {0}")
    @EnumSource(Database::class)
    fun introspectionRequiresClientAuthentication(database: Database) {
        withContainer(database) { sympauthy, _ ->
            val response = httpPostForm(
                discovery(sympauthy)["introspection_endpoint"] as String,
                mapOf("token" to "any-token-value"),
                // Deliberately no Authorization header and no client_id/client_secret in the body.
            )

            assertNotEquals(
                200,
                response.statusCode(),
                "introspection must not succeed without client authentication, body=${response.body()}",
            )
            assertTrue(
                !response.body().contains("\"active\":true"),
                "introspection must not reveal an active token to an unauthenticated caller, body=${response.body()}",
            )
        }
    }
}
