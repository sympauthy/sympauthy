package com.sympauthy.it.security

import com.nimbusds.jose.util.JSONObjectUtils
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **a grant that never requested `openid` receives no `id_token`, at the code
 * exchange nor at the refresh.**
 *
 * Risk: an id token is the only thing this server signs that says who a person is. OpenID Connect
 * Core §3.1.2.1 makes `openid` REQUIRED of an authentication request, and RFC 6749 §5.1 defines no
 * `id_token` in the response to a plain OAuth 2.0 one. Signing an identity assertion for a client
 * that never asked to know the person hands it a credential it can present to anything trusting this
 * issuer, over an authorization the person gave for something else.
 *
 * A confidential client allowed one custom scope and not `openid` signs a user up, exchanges the
 * code, then presents its refresh token. Neither response may carry an `id_token`, on each supported
 * database. That the two answer alike is half the point: they disagreed until issue #453.
 *
 * Source: [OpenID Connect Core 1.0 §3.1.2.1 (Authentication Request)](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest)
 * and [RFC 6749 §5.1 (Successful Response)](https://datatracker.ietf.org/doc/html/rfc6749#section-5.1).
 */
@Tag("security")
class IdTokenOnlyForOpenIdGrantIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a grant without openid receives no id_token, at the exchange or the refresh, on {0}")
    @EnumSource(Database::class)
    fun grantWithoutOpenIdReceivesNoIdToken(database: Database) {
        val plainOAuth2 = mapOf(
            "auth" to mapOf("token" to mapOf("refresh-enabled" to true)),
            // No granting rule names REPORTS_SCOPE, so the default behaviour is what grants it.
            "features" to mapOf("grant-unhandled-scopes" to true),
            "scopes" to mapOf(REPORTS_SCOPE to mapOf("enabled" to true)),
            "clients" to mapOf(clientId to mapOf("allowed-scopes" to listOf(REPORTS_SCOPE))),
        )
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, plainOAuth2, confidentialClient, listOf(REPORTS_SCOPE)) { sympauthy, registry ->
            val tokens = registry.newFlow()
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }
                .run()
                .exchange()

            assertNotNull(tokens.accessToken(), "a plain OAuth 2.0 grant should still yield an access token")
            assertEquals(
                REPORTS_SCOPE,
                tokens.scope(),
                "the grant under test must be a real one, and not an OpenID Connect one",
            )
            assertNull(tokens.idToken(), "a grant without openid must receive no id_token at the code exchange")
            val refreshToken = checkNotNull(tokens.refreshToken()) { "refresh-enabled should yield a refresh token" }

            val response = httpPostForm(
                discovery(sympauthy).tokenEndpoint,
                mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken),
                mapOf("Authorization" to basicAuth(registry.clientId(), checkNotNull(registry.clientSecret()))),
            )
            assertEquals(200, response.statusCode(), "the refresh grant should succeed, body=${response.body()}")

            assertNull(
                JSONObjectUtils.parse(response.body())["id_token"],
                "a grant without openid must receive no id_token at the refresh either, body=${response.body()}",
            )
        }
    }

    private companion object {
        const val CLIENT_SECRET = "confidential-flow-secret-value"
        const val REPORTS_SCOPE = "reports"
    }
}
