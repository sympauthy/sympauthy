package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **an authorization code is single-use: after one successful exchange, replaying
 * the same code must be rejected with `invalid_grant` (HTTP 400).**
 *
 * Risk: per RFC 6749 §10.5 and OAuth 2.1 §4.1.3 an authorization code must be usable at most once. If a
 * code could be redeemed twice, an attacker who recovers an already-used code (from logs, browser
 * history, or the back button) could mint a second, independent set of tokens. The server must deny
 * the replay — enforced here by the single-use authorization code store.
 *
 * This drives a real sign-up, exchanges the code once (which succeeds), then re-posts the same code and
 * asserts a 400 `invalid_grant`, on each supported database.
 *
 * Source: [RFC 6749 §10.5 (Authorization Codes)](https://datatracker.ietf.org/doc/html/rfc6749#section-10.5)
 * and [OAuth 2.1 §4.1.3](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-4.1.3).
 */
@Tag("security")
class AuthorizationCodeReplayIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a used authorization code cannot be replayed on {0}")
    @EnumSource(Database::class)
    fun authorizationCodeCannotBeReplayed(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val result = registry.newFlow()
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }
                .run()
            val code = checkNotNull(result.code()) { "expected an authorization code from sign-up" }

            // First exchange succeeds (the mock frontend replays the matching PKCE verifier).
            val tokens = result.exchange()
            assertNotNull(tokens.accessToken(), "the first exchange should succeed and return an access token")

            // Replaying the very same code must now be denied.
            val replay = httpPostForm(
                discovery(sympauthy)["token_endpoint"] as String,
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to registry.redirectUri(),
                    "client_id" to registry.clientId(),
                    "code_verifier" to generatePkce().verifier,
                ),
            )

            assertEquals(400, replay.statusCode(), "a replayed code must be rejected, body=${replay.body()}")
            assertTrue(
                replay.body().contains("invalid_grant"),
                "expected an invalid_grant error, was: ${replay.body()}",
            )
        }
    }
}
