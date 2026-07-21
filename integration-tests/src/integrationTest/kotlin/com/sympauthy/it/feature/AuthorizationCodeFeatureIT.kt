package com.sympauthy.it.feature

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.flow.FlowStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **OAuth 2.1 authorization-code grant with PKCE, from sign-up to signed tokens.**
 *
 * A user signs up through the mock interactive frontend, the captured authorization code is exchanged
 * at the token endpoint, and the returned id_token is verified against the key set the server
 * advertises at its `jwks_uri`. This proves the end-to-end flow, token issuance and JWT signing all
 * work on the running native image, against each supported database.
 *
 * Reference: [RFC 7636 (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636) and
 * [OpenID Connect Core 1.0, ID Token](https://openid.net/specs/openid-connect-core-1_0.html#IDToken).
 */
@Tag("feature")
class AuthorizationCodeFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "authorization code + PKCE yields a signed id_token on {0}")
    @EnumSource(Database::class)
    fun signsUpAndExchangesCodeForSignedTokens(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val flow = registry.newFlow()
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }

            val result = flow.run()

            assertEquals(listOf(FlowStep.Type.SIGN_UP, FlowStep.Type.COMPLETED), flow.stepTypes())
            assertNotNull(result.code(), "should receive an authorization code")

            val tokens = result.exchange()
            assertNotNull(tokens.accessToken(), "token response should carry an access token")
            assertNotNull(tokens.idToken(), "the openid scope should yield an id_token")

            // Verify the id_token is genuinely signed by the key the server publishes at its jwks_uri.
            val claims = verifyIdTokenSignature(sympauthy, tokens.idToken())
            assertEquals(sympauthy.issuerUrl, claims.issuer, "id_token should be issued by this container")
            assertTrue(
                claims.audience.contains(clientId),
                "id_token audience should be the client, was: ${claims.audience}",
            )
            assertNotNull(claims.subject, "id_token should identify a subject")
        }
    }
}
