package com.sympauthy.it.compliance

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Compliance scenario — **an authorization code is exchanged once: an exchange replaying everything the
 * first one sent is refused, and revokes the grant the code was issued under.**
 *
 * Risk: per RFC 6749 §10.5 and OAuth 2.1 §4.1.3 an authorization code must be usable at most once. If a
 * code could be redeemed twice, whoever recovers it together with its verifier could mint a second,
 * independent set of tokens. RFC 6749 §4.1.2 adds that the server should revoke the tokens already
 * issued for the code, which is what takes back the set of whichever exchange was not the client's.
 *
 * A confidential client signs a person up, exchanges the code and refreshes, then exchanges the code a
 * second time with the same credentials and the same verifier, and asserts the replay is refused and that
 * the access and refresh tokens of the first exchange and the access token of the refresh, active before
 * it, introspect as inactive after — every one of them is under the grant the code names — on each
 * supported database.
 *
 * The replay presents the verifier the flow generated, as the first exchange did: a replay with any other
 * is refused by PKCE before the server reads whether the code was exchanged, and revokes nothing.
 *
 * Source: [RFC 6749 §10.5 (Authorization Codes)](https://datatracker.ietf.org/doc/html/rfc6749#section-10.5),
 * [RFC 6749 §4.1.2](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2)
 * and [OAuth 2.1 §4.1.3](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-4.1.3).
 */
@Tag("compliance")
class AuthorizationCodeReplayIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a used authorization code cannot be replayed on {0}")
    @EnumSource(Database::class)
    fun authorizationCodeCannotBeReplayed(database: Database) {
        val refreshEnabled = mapOf("auth" to mapOf("token" to mapOf("refresh-enabled" to true)))
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, refreshEnabled, confidentialClient) { sympauthy, registry ->
            val result = signUp(registry)
            val tokens = exchange(sympauthy, registry, result)
            val accessToken = tokens.accessToken
            val refreshToken = checkNotNull(tokens.refreshToken) { "refresh-enabled should yield a refresh token" }
            val refreshedAccessToken = refresh(sympauthy, registry, refreshToken).accessToken

            assertEquals(
                true, introspect(sympauthy, accessToken, registry).active,
                "the access token of the first exchange should introspect as active",
            )
            assertEquals(
                true, introspect(sympauthy, refreshedAccessToken, registry).active,
                "the access token of the refresh should introspect as active",
            )
            assertEquals(
                true, introspect(sympauthy, refreshToken, registry).active,
                "the refresh token of the first exchange should introspect as active",
            )

            assertExchangeRefused(
                sympauthy, registry, result, 400, "invalid_grant",
                "a replayed code must be refused",
            )

            assertEquals(
                false, introspect(sympauthy, accessToken, registry).active,
                "a replayed code must revoke the access token the first exchange was issued",
            )
            assertEquals(
                false, introspect(sympauthy, refreshToken, registry).active,
                "a replayed code must revoke the refresh token the first exchange was issued",
            )
            assertEquals(
                false, introspect(sympauthy, refreshedAccessToken, registry).active,
                "a replayed code must revoke the tokens every refresh descended from the first exchange issued",
            )
        }
    }

    private companion object {
        const val CLIENT_SECRET = "authorization-code-replay-secret-value"
    }
}
