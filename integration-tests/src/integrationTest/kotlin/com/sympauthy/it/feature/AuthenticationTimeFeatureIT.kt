package com.sympauthy.it.feature

import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **every token issued for a person says when that person proved a credential,
 * and a refresh reissues that moment unchanged.**
 *
 * A confidential client signs a person up and exchanges the code. The id token and the access token
 * must both state `auth_time`, no later than their own `iat`, and introspection must report the same
 * second. The client then refreshes: the tokens it gets back are new, and the `auth_time` on them is
 * still the original one — which is the whole point of the claim, since `iat` moves on every refresh
 * and would let an authentication a month old read as a minute old.
 *
 * The same run mints a `client_credentials` token and asserts it states none: no person proved
 * anything behind it, and a resource server deciding recency off a date there would be reading the
 * moment a backend asked.
 *
 * Reference: [OpenID Connect Core 1.0 §2 (`auth_time`)](https://openid.net/specs/openid-connect-core-1_0.html#IDToken),
 * [RFC 9068 §2.2.1 (`auth_time` in a JWT access token)](https://www.rfc-editor.org/rfc/rfc9068#section-2.2.1)
 * and [RFC 9470 §6.2 (`auth_time` in introspection)](https://www.rfc-editor.org/rfc/rfc9470#section-6.2).
 */
@Tag("feature")
class AuthenticationTimeFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "every token of an authorization states the moment it authenticated on {0}")
    @EnumSource(Database::class)
    fun tokensStateWhenThePersonAuthenticated(database: Database) {
        withContainer(database, REFRESH_ENABLED, confidentialClient()) { sympauthy, registry ->
            val tokens = signUpAndExchange(registry)

            val idToken = requireIdToken(tokens.idToken())
            val accessToken = checkNotNull(tokens.accessToken()) { "the exchange should yield an access token" }

            val idClaims = verifyIdTokenSignature(sympauthy, idToken)
            val authTime = checkNotNull(idClaims.getLongClaim(AUTH_TIME)) {
                "the id_token must state when the person proved a credential"
            }
            assertTrue(
                authTime <= idClaims.issueTime.toInstant().epochSecond,
                "auth_time is the moment the credential verified, so it cannot be after the token's iat",
            )
            assertEquals(
                authTime,
                claimsOf(accessToken).getLongClaim(AUTH_TIME),
                "the access token must state the same moment, so a resource server can read it too",
            )
            assertEquals(
                authTime,
                introspectedAuthTime(sympauthy, registry, accessToken),
                "introspection must report the same moment",
            )
        }
    }

    @ParameterizedTest(name = "a refresh reissues the original authentication time on {0}")
    @EnumSource(Database::class)
    fun refreshReissuesTheOriginalAuthenticationTime(database: Database) {
        withContainer(database, REFRESH_ENABLED, confidentialClient()) { sympauthy, registry ->
            val tokens = signUpAndExchange(registry)
            val idToken = requireIdToken(tokens.idToken())
            val refreshToken = checkNotNull(tokens.refreshToken()) { "refresh-enabled should yield a refresh token" }
            val authTime = checkNotNull(verifyIdTokenSignature(sympauthy, idToken).getLongClaim(AUTH_TIME))

            val body = refresh(sympauthy, registry, refreshToken)
            val refreshedIdToken = checkNotNull(JSONObjectUtils.getString(body, "id_token"))
            val refreshedAccessToken = JSONObjectUtils.getString(body, "access_token")

            assertEquals(
                authTime,
                verifyIdTokenSignature(sympauthy, refreshedIdToken).getLongClaim(AUTH_TIME),
                "a refreshed id_token states the original authentication, not the moment it was minted",
            )
            assertEquals(
                authTime,
                claimsOf(refreshedAccessToken).getLongClaim(AUTH_TIME),
                "a refreshed access token states the original authentication too",
            )
            assertEquals(
                authTime,
                introspectedAuthTime(sympauthy, registry, refreshedAccessToken),
                "introspecting a refreshed token reports the original authentication",
            )
        }
    }

    @ParameterizedTest(name = "a client_credentials token states no authentication time on {0}")
    @EnumSource(Database::class)
    fun clientCredentialsTokenStatesNoAuthenticationTime(database: Database) {
        withContainer(database, BACKEND_CLIENT, confidentialClient()) { sympauthy, _ ->
            val response = httpPostForm(
                discovery(sympauthy).tokenEndpoint,
                mapOf("grant_type" to "client_credentials"),
                basicAuth(BACKEND_CLIENT_ID, BACKEND_CLIENT_SECRET),
            )
            assertEquals(
                200,
                response.statusCode(),
                "client_credentials should succeed, body=${response.body()}",
            )
            val token = JSONObjectUtils.getString(JSONObjectUtils.parse(response.body()), "access_token")

            assertNull(
                claimsOf(token).getClaim(AUTH_TIME),
                "no person proved a credential behind a client_credentials token",
            )
            assertNull(
                introspectedAuthTime(sympauthy, basicAuth(BACKEND_CLIENT_ID, BACKEND_CLIENT_SECRET), token),
                "introspecting one must not report an authentication that never happened",
            )
        }
    }

    /**
     * The claims [token] carries, read without verifying its signature: what these scenarios assert is
     * which claims an access token states, and that it is genuinely signed is
     * [another scenario's][com.sympauthy.it.security.UserInfoRejectsInvalidBearerIT] subject.
     */
    private fun claimsOf(token: String) = SignedJWT.parse(token).jwtClaimsSet

    private fun introspectedAuthTime(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        token: String,
    ): Long? = introspectedAuthTime(sympauthy, basicAuth(registry), token)

    private fun introspectedAuthTime(
        sympauthy: SympauthyContainer,
        auth: Map<String, String>,
        token: String,
    ): Long? {
        val body = introspect(sympauthy, token, auth)
        assertTrue(JSONObjectUtils.getBoolean(body, "active"), "the token should introspect as active")
        return if (body.containsKey(AUTH_TIME)) JSONObjectUtils.getLong(body, AUTH_TIME) else null
    }

    private fun confidentialClient() = Client.confidentialClient(clientId, CLIENT_SECRET)

    private companion object {
        const val AUTH_TIME = "auth_time"
        const val CLIENT_SECRET = "authentication-time-secret-value"
        const val BACKEND_CLIENT_ID = "authentication-time-backend"
        const val BACKEND_CLIENT_SECRET = "authentication-time-backend-secret"
        val REFRESH_ENABLED = mapOf("auth" to mapOf("token" to mapOf("refresh-enabled" to true)))

        /** A second client that holds a secret and nothing else: the one grant it may use has no person. */
        val BACKEND_CLIENT = mapOf(
            "clients" to mapOf(
                BACKEND_CLIENT_ID to mapOf(
                    "public" to false,
                    "secret" to BACKEND_CLIENT_SECRET,
                    "allowed-grant-types" to listOf("client_credentials"),
                ),
            ),
        )
    }
}
