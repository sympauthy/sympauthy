package com.sympauthy.it.feature

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **every token of an authorization is issued under one grant, and a revocation or a
 * lifetime reaches the grant rather than one token.**
 *
 * The first two scenarios sign a person up, exchange the code and refresh twice: every access token issued
 * along the way is active, being under the one grant the authorization opened. The client then revokes one
 * token, and the grant goes with it — the latest access token, after which the refresh token is refused at
 * the next refresh, or the refresh token itself; either way every access token of the grant introspects as
 * inactive.
 *
 * The third gives grants a lifetime of a few seconds and a refresh token of a month: a refresh inside the
 * lifetime is issued tokens that expire with the grant, and one made after it is refused however recently
 * the refresh token was issued.
 *
 * Source: [sympauthy#584](https://github.com/sympauthy/sympauthy/issues/584), and
 * [RFC 7009 §2.1](https://datatracker.ietf.org/doc/html/rfc7009#section-2.1), which lets the server revoke
 * the tokens of the same grant as the one presented — the refresh token when an access token is presented.
 */
@Tag("feature")
class GrantFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "revoking the latest access token revokes the grant it was issued under on {0}")
    @EnumSource(Database::class)
    fun revokingAnAccessTokenRevokesItsGrant(database: Database) {
        withRefreshedGrant(database) { sympauthy, registry, refreshToken, accessTokens ->
            revoke(sympauthy, accessTokens.last(), registry)

            assertRefreshRefused(
                sympauthy, registry, refreshToken, 400, "invalid_grant",
                "the refresh token of a grant revoked through its access token must be refused",
            )
            assertAllInactive(sympauthy, registry, accessTokens)
        }
    }

    @ParameterizedTest(name = "revoking the refresh token revokes the grant it was issued under on {0}")
    @EnumSource(Database::class)
    fun revokingTheRefreshTokenRevokesItsGrant(database: Database) {
        withRefreshedGrant(database) { sympauthy, registry, refreshToken, accessTokens ->
            revoke(sympauthy, refreshToken, registry)

            assertAllInactive(sympauthy, registry, accessTokens)
        }
    }

    @ParameterizedTest(name = "a grant can no longer be refreshed once its lifetime has passed on {0}")
    @EnumSource(Database::class)
    fun refreshingPastTheGrantLifetimeIsRefused(database: Database) {
        val shortGrants = mapOf(
            "auth" to mapOf(
                "token" to mapOf(
                    "refresh-enabled" to true,
                    "refresh-expiration" to "30d",
                    "grant-expiration" to "${GRANT_LIFETIME_SECONDS}s",
                ),
            ),
        )
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, shortGrants, confidentialClient) { sympauthy, registry ->
            val tokens = signUpAndExchange(sympauthy, registry)
            val refreshToken = checkNotNull(tokens.refreshToken) { "refresh-enabled should yield a refresh token" }

            val refreshed = refresh(sympauthy, registry, refreshToken)
            val expiresIn = checkNotNull(refreshed.expiresIn) { "the refreshed access token should expire" }.toLong()
            assertTrue(
                expiresIn <= GRANT_LIFETIME_SECONDS,
                "an access token must expire no later than its grant, expires_in=$expiresIn",
            )

            // Past the lifetime of the grant, while the refresh token alone would live a month.
            Thread.sleep((GRANT_LIFETIME_SECONDS + 1) * 1000)

            assertRefreshRefused(
                sympauthy, registry, refreshToken, 400, "invalid_grant",
                "a refresh past the lifetime of the grant must be refused",
            )
            assertEquals(
                false, introspect(sympauthy, refreshToken, registry).active,
                "the refresh token of a grant past its lifetime must introspect as inactive",
            )
        }
    }

    /**
     * Signs a person up through a confidential client, exchanges the code and refreshes twice, asserts that
     * every access token issued along the way is active, and runs [block] with the refresh token and those
     * access tokens, the one the exchange issued first.
     */
    private fun withRefreshedGrant(
        database: Database,
        block: (SympauthyContainer, InteractiveFlowRegistry, String, List<String>) -> Unit,
    ) {
        val refreshEnabled = mapOf("auth" to mapOf("token" to mapOf("refresh-enabled" to true)))
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, refreshEnabled, confidentialClient) { sympauthy, registry ->
            val tokens = signUpAndExchange(sympauthy, registry)
            val refreshToken = checkNotNull(tokens.refreshToken) { "refresh-enabled should yield a refresh token" }
            val accessTokens = listOf(tokens.accessToken) +
                List(2) { refresh(sympauthy, registry, refreshToken).accessToken }

            accessTokens.forEachIndexed { index, accessToken ->
                assertEquals(
                    true, introspect(sympauthy, accessToken, registry).active,
                    "access token $index of the grant should introspect as active",
                )
            }
            block(sympauthy, registry, refreshToken, accessTokens)
        }
    }

    private fun assertAllInactive(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        accessTokens: List<String>,
    ) {
        accessTokens.forEachIndexed { index, accessToken ->
            assertEquals(
                false, introspect(sympauthy, accessToken, registry).active,
                "access token $index must be revoked with the grant it was issued under",
            )
        }
    }

    private companion object {
        const val CLIENT_SECRET = "grant-feature-secret-value"

        /** Long enough for a sign-up's exchange and one refresh to fit inside it, and short enough to wait out. */
        const val GRANT_LIFETIME_SECONDS = 3L
    }
}
