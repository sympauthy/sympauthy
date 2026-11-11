package com.sympauthy.it.compliance

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Compliance scenario — **an access token bound to a DPoP key is served only under the `DPoP` scheme with
 * a proof signed by that key, and is refused as a bearer token.**
 *
 * Risk: binding a token to the client's key is what makes a stolen token useless on its own. If a
 * resource on this server accepted the bound token as `Authorization: Bearer`, the binding would be
 * issued and never checked, and whoever holds the token would simply present it the old way. If the
 * `DPoP` scheme were not read at all, a client honouring its binding would have no way to use the token.
 *
 * This signs a person up, exchanges the code beside a DPoP proof for a token bound to that proof's key,
 * then calls UserInfo with it twice: as `Authorization: DPoP` with a fresh proof, which is served, and
 * as `Authorization: Bearer` with none, which is refused — on each supported database.
 *
 * Source: [RFC 9449 §7.1 and §7.2](https://datatracker.ietf.org/doc/html/rfc9449#section-7), and
 * [sympauthy#529](https://github.com/sympauthy/sympauthy/issues/529).
 */
@Tag("compliance")
class DpopBoundAccessTokenRefusedAsBearerIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a DPoP-bound access token is refused as a bearer token on {0}")
    @EnumSource(Database::class)
    fun boundAccessTokenIsServedUnderDpopAndRefusedAsBearer(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val key = ECKeyGenerator(Curve.P_256).generate()
            val accessToken = exchangeForBoundToken(sympauthy, registry, signUp(registry), key)
            val userInfoEndpoint = discovery(sympauthy).userinfoEndpoint!!

            val underDpop = httpGet(
                userInfoEndpoint,
                headers = mapOf(
                    "Authorization" to "DPoP $accessToken",
                    "DPoP" to dpopProof(key, "GET", userInfoEndpoint, accessToken),
                ),
            )
            assertEquals(
                200,
                underDpop.statusCode(),
                "userinfo must serve a bound token presented with a proof of its key, body=${underDpop.body()}",
            )

            val asBearer = httpGet(userInfoEndpoint, headers = mapOf("Authorization" to "Bearer $accessToken"))
            assertError(
                asBearer, 401, "dpop.bound_token_as_bearer",
                "userinfo must refuse a bound token presented as a bearer token",
            )
        }
    }
}
