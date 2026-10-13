package com.sympauthy.it.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Date
import java.util.UUID

/**
 * Security scenario — **an access token bound to a DPoP key is served only under the `DPoP` scheme with
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
@Tag("security")
class DpopBoundAccessTokenRefusedAsBearerIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a DPoP-bound access token is refused as a bearer token on {0}")
    @EnumSource(Database::class)
    fun boundAccessTokenIsServedUnderDpopAndRefusedAsBearer(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val key = ECKeyGenerator(Curve.P_256).generate()
            val accessToken = boundAccessToken(sympauthy, registry, key)
            val userInfoEndpoint = discovery(sympauthy).userinfoEndpoint!!

            val underDpop = httpGet(
                userInfoEndpoint,
                headers = mapOf(
                    "Authorization" to "DPoP $accessToken",
                    "DPoP" to proof(key, "GET", userInfoEndpoint, accessToken),
                ),
            )
            assertEquals(
                200,
                underDpop.statusCode(),
                "userinfo must serve a bound token presented with a proof of its key, body=${underDpop.body()}",
            )

            val asBearer = httpGet(userInfoEndpoint, headers = mapOf("Authorization" to "Bearer $accessToken"))
            assertOAuthError(
                asBearer, 401, "dpop.bound_token_as_bearer",
                "userinfo must refuse a bound token presented as a bearer token",
            )
        }
    }

    /**
     * The access token a sign-up through [registry]'s flow is issued when its code is exchanged beside a
     * proof signed by [key], which binds the token to that key.
     */
    private fun boundAccessToken(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        key: ECKey,
    ): String {
        val pkce = generatePkce()
        val authorize = httpGet(authorizeUrl(sympauthy, registry, pkce))
        assertEquals(303, authorize.statusCode(), "authorize should redirect to the first step")
        val firstStep = authorize.headers().firstValue("Location").orElseGet { error("authorize 303 had no Location") }
        val result = registry.newFlow()
            .withSignUpHandler { credentials() }
            .driveFrom(firstStep, registry.redirectUri(), registry.redirectUri())
            .drive()
        val code = checkNotNull(result.terminalParam("code")) { "expected an authorization code from sign-up" }

        val tokenEndpoint = discovery(sympauthy).tokenEndpoint
        val response = exchangeCode(
            sympauthy, registry, code,
            overrides = mapOf("code_verifier" to pkce.verifier),
            headers = mapOf("DPoP" to proof(key, "POST", tokenEndpoint, accessToken = null)),
        )
        assertEquals(200, response.statusCode(), "the code exchange should succeed, body=${response.body()}")
        val body = JSONObjectUtils.parse(response.body())
        assertEquals("DPoP", body["token_type"], "a token request beside a proof should be issued a bound token")
        return body["access_token"] as String
    }

    /**
     * A DPoP proof signed by [key] for a request of [method] to [url], carrying the hash of [accessToken]
     * where the request presents one.
     */
    private fun proof(key: ECKey, method: String, url: String, accessToken: String?): String {
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(key.toPublicJWK())
            .build()
        val claims = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .claim("htm", method)
            .claim("htu", url)
            .issueTime(Date())
            .apply { accessToken?.let { claim("ath", hash(it)) } }
            .build()
        return SignedJWT(header, claims).apply { sign(ECDSASigner(key)) }.serialize()
    }

    private fun hash(accessToken: String): String = Base64URL.encode(
        MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(StandardCharsets.US_ASCII)),
    ).toString()
}
