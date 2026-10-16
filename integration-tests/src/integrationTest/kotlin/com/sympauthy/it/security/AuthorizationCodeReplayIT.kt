package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.client.SympauthyApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **an authorization code is exchanged once: an exchange replaying everything the
 * first one sent is refused, and revokes the tokens the first one was issued.**
 *
 * Risk: per RFC 6749 §10.5 and OAuth 2.1 §4.1.3 an authorization code must be usable at most once. If a
 * code could be redeemed twice, whoever recovers it together with its verifier could mint a second,
 * independent set of tokens. RFC 6749 §4.1.2 adds that the server should revoke the tokens already
 * issued for the code, which is what takes back the set of whichever exchange was not the client's.
 *
 * A confidential client signs a person up and exchanges the code, then exchanges it a second time with
 * the same credentials and the same verifier, and asserts the replay is refused and that the access and
 * refresh tokens of the first exchange, active before it, introspect as inactive after — on each
 * supported database.
 *
 * The replay goes through the driver's own exchange, the one request carrying the verifier the flow
 * generated: a replay with any other is refused by PKCE before the code is read. The driver answers a
 * refused exchange with an exception rather than the response, so the refusal is read off the
 * revocation, which only the refusal of a code already exchanged performs.
 *
 * Source: [RFC 6749 §10.5 (Authorization Codes)](https://datatracker.ietf.org/doc/html/rfc6749#section-10.5),
 * [RFC 6749 §4.1.2](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2)
 * and [OAuth 2.1 §4.1.3](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-4.1.3).
 */
@Tag("security")
class AuthorizationCodeReplayIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a used authorization code cannot be replayed on {0}")
    @EnumSource(Database::class)
    fun authorizationCodeCannotBeReplayed(database: Database) {
        val refreshEnabled = mapOf("auth" to mapOf("token" to mapOf("refresh-enabled" to true)))
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, refreshEnabled, confidentialClient) { sympauthy, registry ->
            val result = signUp(registry)
            val tokens = result.exchange()
            val accessToken = checkNotNull(tokens.accessToken()) { "the first exchange should return an access token" }
            val refreshToken = checkNotNull(tokens.refreshToken()) { "refresh-enabled should yield a refresh token" }
            val auth = basicAuth(registry)

            assertEquals(
                true, introspect(sympauthy, accessToken, auth)["active"],
                "the access token of the first exchange should introspect as active",
            )
            assertEquals(
                true, introspect(sympauthy, refreshToken, auth)["active"],
                "the refresh token of the first exchange should introspect as active",
            )

            assertThrows<SympauthyApiException>("a replayed code must be refused") { result.exchange() }

            assertEquals(
                false, introspect(sympauthy, accessToken, auth)["active"],
                "a replayed code must revoke the access token the first exchange was issued",
            )
            assertEquals(
                false, introspect(sympauthy, refreshToken, auth)["active"],
                "a replayed code must revoke the refresh token the first exchange was issued",
            )
        }
    }

    private companion object {
        const val CLIENT_SECRET = "authorization-code-replay-secret-value"
    }
}
