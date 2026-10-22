package com.sympauthy.it.compliance

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Compliance scenario — **the UserInfo endpoint must reject a request that carries no bearer token, an
 * invalid one, or one that does not give access, never returning claims, and must challenge each.**
 *
 * Risk: UserInfo exposes the subject's OpenID claims and is protected solely by the access token
 * (RFC 6750). If it served claims to an unauthenticated caller, or accepted a forged/garbage bearer,
 * any party could read a user's profile. The endpoint is `@Secured(IS_USER)`, so a missing or
 * unverifiable bearer must yield a 401, and a `client_credentials` token, which has no end-user behind
 * it, a 403. A refusal without a `WWW-Authenticate` challenge leaves a client unable to tell from the
 * response how it is expected to authenticate, which RFC 6750 §3 requires the challenge to say. A
 * token is read from the `Authorization` header alone: one a browser sent along in a cookie would let
 * any page the wildcard CORS policy admits act on behalf of whoever holds it.
 *
 * This calls UserInfo on the running native image with (a) no `Authorization` header, (b) a garbage
 * bearer, (c) a token issued by the `client_credentials` grant and (d) a valid access token in the
 * framework's `JWT` cookie and no `Authorization` header, asserting the status and the challenge of
 * each, on each supported database.
 *
 * Source: [RFC 6750 §3 (The WWW-Authenticate Response Header Field)](https://datatracker.ietf.org/doc/html/rfc6750#section-3),
 * [RFC 9449 §7.2 (Compatibility with the Bearer Authentication Scheme)](https://datatracker.ietf.org/doc/html/rfc9449#section-7.2)
 * and issues [#533](https://github.com/sympauthy/sympauthy/issues/533) and
 * [#598](https://github.com/sympauthy/sympauthy/issues/598).
 */
@Tag("compliance")
class UserInfoRejectsInvalidBearerIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "userinfo rejects a missing/invalid bearer with 401 on {0}")
    @EnumSource(Database::class)
    fun userInfoRejectsMissingOrInvalidBearer(database: Database) {
        withContainer(database) { sympauthy, _ ->
            val userInfoEndpoint = discovery(sympauthy).userinfoEndpoint!!

            val noToken = httpGet(userInfoEndpoint)
            assertEquals(401, noToken.statusCode(), "userinfo must require a bearer token, body=${noToken.body()}")
            assertEquals(
                """Bearer realm="sympauthy", DPoP realm="sympauthy", algs="$ALGS"""",
                noToken.headers().firstValue("WWW-Authenticate").orElse(null),
                "a request carrying no token must be challenged with no error",
            )

            val garbageToken = httpGet(
                userInfoEndpoint,
                headers = mapOf("Authorization" to "Bearer not-a-real-token"),
            )
            assertEquals(
                401,
                garbageToken.statusCode(),
                "userinfo must reject an unverifiable bearer, body=${garbageToken.body()}",
            )
            assertEquals(
                """Bearer realm="sympauthy", error="invalid_token", DPoP realm="sympauthy", algs="$ALGS"""",
                garbageToken.headers().firstValue("WWW-Authenticate").orElse(null),
                "an unverifiable bearer must be challenged with invalid_token",
            )
        }
    }

    @ParameterizedTest(name = "userinfo rejects a client_credentials token with 403 on {0}")
    @EnumSource(Database::class)
    fun userInfoRejectsClientCredentialsToken(database: Database) {
        val confidentialClient = mapOf(
            "clients" to mapOf(
                CLIENT_ID to mapOf(
                    "public" to false,
                    "secret" to CLIENT_SECRET,
                    "allowed-grant-types" to listOf("client_credentials"),
                ),
            ),
        )
        withContainer(database, confidentialClient) { sympauthy, _ ->
            val accessToken = clientCredentialsToken(sympauthy, CLIENT_ID, CLIENT_SECRET)

            val response = httpGet(
                discovery(sympauthy).userinfoEndpoint!!,
                headers = mapOf("Authorization" to "Bearer $accessToken"),
            )

            assertEquals(
                403,
                response.statusCode(),
                "userinfo must refuse a token with no end-user, body=${response.body()}",
            )
            assertEquals(
                """Bearer realm="sympauthy", error="insufficient_scope", DPoP realm="sympauthy", algs="$ALGS"""",
                response.headers().firstValue("WWW-Authenticate").orElse(null),
                "a token that does not give access must be challenged with insufficient_scope",
            )
        }
    }

    /**
     * `JWT` is the cookie Micronaut's `CookieTokenReader` reads by default. The reader is a bean in any
     * cookie-based authentication mode, `idtoken` among them, unless `micronaut.security.token.cookie`
     * is disabled. Configuring such a mode again would let a browser send a token along that no surface
     * here is meant to accept.
     */
    @ParameterizedTest(name = "userinfo ignores an access token sent in a cookie on {0}")
    @EnumSource(Database::class)
    fun userInfoIgnoresAccessTokenInCookie(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val accessToken = signUpAndExchange(sympauthy, registry).accessToken

            val response = httpGet(
                discovery(sympauthy).userinfoEndpoint!!,
                headers = mapOf("Cookie" to "JWT=$accessToken"),
            )

            assertEquals(
                401,
                response.statusCode(),
                "userinfo must not read an access token from a cookie, body=${response.body()}",
            )
            assertEquals(
                """Bearer realm="sympauthy", DPoP realm="sympauthy", algs="$ALGS"""",
                response.headers().firstValue("WWW-Authenticate").orElse(null),
                "a request carrying its token only in a cookie must be challenged as one carrying none",
            )
        }
    }

    private companion object {
        const val CLIENT_ID = "userinfo-confidential-client"
        const val CLIENT_SECRET = "userinfo-confidential-client-secret"
        const val ALGS = "RS256 RS384 RS512 ES256 ES384 ES512 PS256 PS384 PS512"
    }
}
