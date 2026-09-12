package com.sympauthy.it.feature

import com.nimbusds.jose.util.JSONObjectUtils
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **the refresh grant reissues the identity, not only the access token.**
 *
 * A confidential client signs a user up, exchanges the code, then presents the refresh token at the
 * token endpoint. The response must carry a new `id_token` beside the new `access_token`: the same
 * subject and audience as the one issued at sign-in, freshly signed, and binding the access token
 * returned with it rather than the one it replaced. That last part is what makes a refreshed id token
 * safe to accept — a stale `at_hash` would let the substitution mitigation of §3.1.3.6 pass on a token
 * pair it was never computed over.
 *
 * Reference: [OpenID Connect Core 1.0 §12.2 (Successful Refresh Response)](https://openid.net/specs/openid-connect-core-1_0.html#RefreshingAccessToken).
 */
@Tag("feature")
class RefreshGrantFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "the refresh grant issues an id_token bound to the new access token on {0}")
    @EnumSource(Database::class)
    fun refreshIssuesIdTokenBoundToNewAccessToken(database: Database) {
        val refreshEnabled = mapOf("auth" to mapOf("token" to mapOf("refresh-enabled" to true)))
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, refreshEnabled, confidentialClient) { sympauthy, registry ->
            val tokens = registry.newFlow()
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }
                .run()
                .exchange()
            val idToken = checkNotNull(tokens.idToken()) { "the openid scope should yield an id_token" }
            val refreshToken = checkNotNull(tokens.refreshToken()) { "refresh-enabled should yield a refresh token" }

            val response = httpPostForm(
                discovery(sympauthy).tokenEndpoint,
                mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken),
                mapOf("Authorization" to basicAuth(registry.clientId(), checkNotNull(registry.clientSecret()))),
            )
            assertEquals(200, response.statusCode(), "the refresh grant should succeed, body=${response.body()}")

            val body = JSONObjectUtils.parse(response.body())
            val refreshedAccessToken = JSONObjectUtils.getString(body, "access_token")
            val refreshedIdToken = checkNotNull(JSONObjectUtils.getString(body, "id_token")) {
                "the refresh response should carry an id_token, body=${response.body()}"
            }
            assertNotEquals(idToken, refreshedIdToken, "the refresh grant should issue a new id_token")

            val claims = verifyIdTokenSignature(sympauthy, idToken)
            val refreshedClaims = verifyIdTokenSignature(sympauthy, refreshedIdToken)
            assertEquals(claims.subject, refreshedClaims.subject, "the refreshed id_token names the same subject")
            assertEquals(claims.audience, refreshedClaims.audience, "the refreshed id_token names the same audience")

            assertEquals(
                expectedAtHash(refreshedAccessToken),
                refreshedClaims.getStringClaim("at_hash"),
                "the refreshed id_token must bind the access token issued with it, not the one it replaced",
            )
        }
    }

    private companion object {
        const val CLIENT_SECRET = "confidential-flow-secret-value"
    }
}
