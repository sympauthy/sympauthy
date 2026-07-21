package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **revoking a refresh token also revokes the access token issued in the same
 * authorization session (cascading revocation).**
 *
 * Risk: RFC 7009 §2.1 lets the server also revoke tokens issued from the same authorization grant. If a
 * refresh token leaks and the client revokes it, any access token minted alongside it must stop working
 * too — otherwise the compromised session could not be fully contained. This proves the cascade on the
 * running native image.
 *
 * A confidential client completes the authorization-code flow (with refresh tokens enabled), confirms
 * its access token is active, revokes the **refresh** token, then asserts the access token now
 * introspects as inactive, on each supported database.
 *
 * Source: [RFC 7009 §2.1 (Revocation Request)](https://datatracker.ietf.org/doc/html/rfc7009#section-2.1).
 */
@Tag("security")
class RefreshTokenRevocationCascadesIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "revoking a refresh token also revokes its access token on {0}")
    @EnumSource(Database::class)
    fun revokingRefreshTokenCascadesToAccessToken(database: Database) {
        val refreshEnabled = mapOf("auth" to mapOf("token" to mapOf("refresh-enabled" to true)))
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, refreshEnabled, confidentialClient) { sympauthy, registry ->
            val tokens = registry.newFlow()
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }
                .run()
                .exchange()
            val accessToken = checkNotNull(tokens.accessToken()) { "expected an access token from exchange" }
            val refreshToken = checkNotNull(tokens.refreshToken()) { "refresh-enabled should yield a refresh token" }

            val discovery = discovery(sympauthy)
            val introspectionEndpoint = discovery["introspection_endpoint"] as String
            val auth = mapOf("Authorization" to basicAuth(registry.clientId(), checkNotNull(registry.clientSecret())))

            // The access token is active before revocation.
            val before = httpPostForm(introspectionEndpoint, mapOf("token" to accessToken), auth)
            assertTrue(
                before.body().contains("\"active\":true"),
                "the freshly issued access token should introspect as active, body=${before.body()}",
            )

            // Revoke the *refresh* token.
            val revoke = httpPostForm(
                discovery["revocation_endpoint"] as String,
                mapOf("token" to refreshToken, "token_type_hint" to "refresh_token"),
                auth,
            )
            assertEquals(200, revoke.statusCode(), "revocation should return 200, body=${revoke.body()}")

            // Cascading revocation: the access token from the same session is now inactive too.
            val after = httpPostForm(introspectionEndpoint, mapOf("token" to accessToken), auth)
            assertTrue(
                after.body().contains("\"active\":false"),
                "revoking the refresh token must also revoke the session's access token, body=${after.body()}",
            )
        }
    }

    private companion object {
        const val CLIENT_SECRET = "confidential-flow-secret-value"
    }
}
