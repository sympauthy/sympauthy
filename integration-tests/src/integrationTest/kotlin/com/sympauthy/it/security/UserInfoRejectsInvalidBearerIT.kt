package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the UserInfo endpoint must reject a request that carries no bearer token or an
 * invalid one with `401 Unauthorized`, never returning claims.**
 *
 * Risk: UserInfo exposes the subject's OpenID claims and is protected solely by the access token
 * (RFC 6750). If it served claims to an unauthenticated caller, or accepted a forged/garbage bearer,
 * any party could read a user's profile. The endpoint is `@Secured(IS_USER)`, so a missing or
 * unverifiable bearer must yield a 401.
 *
 * This calls UserInfo on the running native image with (a) no `Authorization` header and (b) a garbage
 * bearer, asserting a 401 for each, on each supported database.
 *
 * Source: [RFC 6750 §3.1 (The WWW-Authenticate Response Header Field)](https://datatracker.ietf.org/doc/html/rfc6750#section-3.1).
 */
@Tag("security")
class UserInfoRejectsInvalidBearerIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "userinfo rejects a missing/invalid bearer with 401 on {0}")
    @EnumSource(Database::class)
    fun userInfoRejectsMissingOrInvalidBearer(database: Database) {
        withContainer(database) { sympauthy, _ ->
            val userInfoEndpoint = discovery(sympauthy)["userinfo_endpoint"] as String

            val noToken = httpGet(userInfoEndpoint)
            assertEquals(401, noToken.statusCode(), "userinfo must require a bearer token, body=${noToken.body()}")

            val garbageToken = httpGet(
                userInfoEndpoint,
                headers = mapOf("Authorization" to "Bearer not-a-real-token"),
            )
            assertEquals(
                401,
                garbageToken.statusCode(),
                "userinfo must reject an unverifiable bearer, body=${garbageToken.body()}",
            )
        }
    }
}
