package com.sympauthy.it.feature

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.api.client.api.OpenidApi
import com.sympauthy.api.client.api.UserApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.http.HttpResponse

/**
 * Feature scenario — **an operator keeps the admin panel's preferences on their own account, in the
 * `sympauthy_admin_preferences` claim the `admin` environment declares.**
 *
 * The container runs the `admin` environment and declares no claim of its own beyond the email the
 * sign-up takes, so every property asserted here is the shipped declaration's. The first operator
 * signs up through the bootstrap invitation and exchanges the code beside a DPoP proof. With that bound
 * token they write a document through `PATCH /api/v1/user/claims` and read it back through `GET`, and
 * are refused a document over 1 KiB and a JSON array. Signing in again without a proof yields a bearer
 * token, whose write is refused with `authentication.unbound_token` on a `DPoP` challenge and whose
 * read answers the claims without the document; the id token and the access token of that sign-in, the
 * `/userinfo` read with it and the discovery document all leave the claim out. A person signing up
 * through a client of the `default` audience is refused the claim with a bound token of their own. A
 * deployment turning the claim off has the first write refused.
 *
 * The shipped file is what is proved, and only a booted server reads it: its claim beside the keys a
 * deployment writes, its doors against a token the server bound itself, and its publication against
 * every place a value could travel.
 *
 * Issue: [#694](https://github.com/sympauthy/sympauthy/issues/694).
 */
@Tag("feature")
class AdminPreferencesFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "an operator keeps the panel preferences on their account on {0}")
    @EnumSource(Database::class)
    fun keepsAnOperatorsPreferences(database: Database) {
        withAdminAndApp(database) { sympauthy, admin, app ->
            val key = ECKeyGenerator(Curve.P_256).generate()
            val boundToken = signUpFirstAdmin(sympauthy, admin, key)

            val written = patchClaims(sympauthy, key, boundToken, mapOf(CLAIM to DOCUMENT))
            assertEquals(200, written.statusCode(), "the bound write should be served, body=${written.body()}")
            assertEquals(DOCUMENT, claimsOf(written)[CLAIM], "the write answers the document as an object")

            val read = getClaims(sympauthy, key, boundToken)
            assertEquals(200, read.statusCode(), "the bound read should be served, body=${read.body()}")
            assertEquals(DOCUMENT, claimsOf(read)[CLAIM], "the bound read answers the document")

            val tooLarge = patchClaims(sympauthy, key, boundToken, mapOf(CLAIM to mapOf("theme" to "x".repeat(1024))))
            assertError(tooLarge, 400, "flow.claims.invalid", "a document over 1 KiB should be refused")
            assertTrue(
                tooLarge.body().contains("user.claim_value_validator.too_large"),
                "the refusal should name the size, body=${tooLarge.body()}",
            )
            val list = patchClaims(sympauthy, key, boundToken, mapOf(CLAIM to listOf("dark")))
            assertError(list, 400, "flow.claims.invalid", "a JSON array should be refused")
            assertTrue(
                list.body().contains("user.claim_value_validator.invalid_json"),
                "the refusal should name the shape, body=${list.body()}",
            )

            val signedIn = exchange(
                sympauthy, admin,
                admin.newFlow().withSignInHandler { Credentials.of(ADMIN_EMAIL, DEFAULT_PASSWORD) }.run(),
            )
            withApiClient(sympauthy, token = signedIn.accessToken) { ctx ->
                val refusal = assertThrows<HttpClientResponseException>("a bearer write should be refused") {
                    ctx.getBean(UserApi::class.java).updateClaims(mutableMapOf(CLAIM to DOCUMENT)).block()
                }
                assertError(refusal, 401, "authentication.unbound_token", "a bearer write should be refused")
                val challenges = refusal.response.headers.getAll("WWW-Authenticate")
                assertTrue(
                    challenges.any { DPOP_CHALLENGE.containsMatchIn(it) },
                    "the refusal should challenge for a bound token, was: $challenges",
                )

                val readByBearer = ctx.getBean(UserApi::class.java).getClaims().block()
                assertFalse(
                    readByBearer?.claims.orEmpty().containsKey(CLAIM),
                    "a bearer read should leave the document out, was: ${readByBearer?.claims}",
                )

                val userInfo = ctx.getBean(OpenidApi::class.java).getUserInfo().block()
                assertFalse(userInfo.orEmpty().containsKey(CLAIM), "/userinfo should leave the document out")
            }
            assertFalse(
                verifyIdTokenSignature(sympauthy, requireIdToken(signedIn.idToken)).claims.containsKey(CLAIM),
                "the id token should leave the document out",
            )
            assertFalse(
                SignedJWT.parse(signedIn.accessToken).jwtClaimsSet.claims.containsKey(CLAIM),
                "the access token should leave the document out",
            )
            assertFalse(
                discovery(sympauthy).claimsSupported.orEmpty().contains(CLAIM),
                "the discovery document should not list the claim",
            )

            val appKey = ECKeyGenerator(Curve.P_256).generate()
            val appToken = exchangeForBoundToken(sympauthy, app, signUp(app, APP_EMAIL), appKey)
            assertError(
                patchClaims(sympauthy, appKey, appToken, mapOf(CLAIM to DOCUMENT)),
                400, "user.invalid_claim",
                "a client of another audience should be refused the claim",
            )
        }
    }

    @ParameterizedTest(name = "a deployment turning the panel preferences off is refused their write on {0}")
    @EnumSource(Database::class)
    fun refusesTheWriteWhenTurnedOff(database: Database) {
        val turnedOff = mapOf("claims" to mapOf(CLAIM to mapOf("enabled" to "false")))
        withAdminAndApp(database, turnedOff) { sympauthy, admin, _ ->
            val key = ECKeyGenerator(Curve.P_256).generate()
            val boundToken = signUpFirstAdmin(sympauthy, admin, key)

            assertError(
                patchClaims(sympauthy, key, boundToken, mapOf(CLAIM to DOCUMENT)),
                400, "user.invalid_claim",
                "a claim the deployment turned off should be refused",
            )
        }
    }

    /**
     * Redeems the first-admin bootstrap invitation through [registry]'s flow, and answers the access token
     * its code is exchanged for beside a proof signed by [key].
     */
    private fun signUpFirstAdmin(sympauthy: SympauthyContainer, registry: InteractiveFlowRegistry, key: ECKey): String {
        val signedUp = registry.newFlow()
            .withInvitationToken(sympauthy.getBootstrapInvitationToken("first-admin"))
            .withSignUpHandler { credentials(ADMIN_EMAIL) }
            .run()
        return exchangeForBoundToken(sympauthy, registry, signedUp, key)
    }

    /**
     * `GET /api/v1/user/claims` under the `DPoP` scheme, with [token] and a proof signed by [key]: the
     * generated client presents a token as a bearer one alone.
     */
    private fun getClaims(sympauthy: SympauthyContainer, key: ECKey, token: String): HttpResponse<String> {
        val url = userClaimsUrl(sympauthy)
        return httpGet(url, headers = dpopHeaders(key, "GET", url, token))
    }

    /** `PATCH /api/v1/user/claims` with [claims] as its body, as [getClaims] presents [token]. */
    private fun patchClaims(
        sympauthy: SympauthyContainer,
        key: ECKey,
        token: String,
        claims: Map<String, Any>,
    ): HttpResponse<String> {
        val url = userClaimsUrl(sympauthy)
        return httpPatch(url, JSONObjectUtils.toJSONString(claims), dpopHeaders(key, "PATCH", url, token))
    }

    private fun dpopHeaders(key: ECKey, method: String, url: String, token: String): Map<String, String> = mapOf(
        "Authorization" to "DPoP $token",
        "DPoP" to dpopProof(key, method, url, token),
    )

    private fun userClaimsUrl(sympauthy: SympauthyContainer): String = "${sympauthy.baseUrl}/api/v1/user/claims"

    @Suppress("UNCHECKED_CAST")
    private fun claimsOf(response: HttpResponse<String>): Map<String, Any?> =
        JSONObjectUtils.parse(response.body())["claims"] as Map<String, Any?>

    /**
     * Starts a container running the `admin` environment, with [extraConfig] merged over it, holding the
     * admin client of [admin] and the public client of [app] in the `default` audience, and runs [block]
     * against it.
     */
    private fun withAdminAndApp(
        database: Database,
        extraConfig: Map<String, Any> = emptyMap(),
        block: (SympauthyContainer, InteractiveFlowRegistry, InteractiveFlowRegistry) -> Unit,
    ) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.publicClient(ADMIN_CLIENT_ID)).use { admin ->
                InteractiveFlowRegistry.forClient(Client.publicClient(APP_CLIENT_ID))
                    .withFlowId(APP_FLOW_ID)
                    .use { app ->
                        container(fixture, config(app) and extraConfig, admin, app)
                            .withAdmin()
                            .withAdminClient(admin, "openid")
                            .use { sympauthy -> withStartedContainer(sympauthy) { block(it, admin, app) } }
                    }
            }
        }
    }

    private companion object {

        const val CLAIM = "sympauthy_admin_preferences"
        const val ADMIN_CLIENT_ID = "admin-console"
        const val ADMIN_EMAIL = "operator@example.com"
        const val APP_CLIENT_ID = "admin-preferences-app"
        const val APP_FLOW_ID = "admin-preferences-flow"
        const val APP_EMAIL = "admin-preferences@example.com"

        /** A `DPoP` challenge, which may follow a `Bearer` one in the same header value. */
        val DPOP_CHALLENGE = Regex("""(^|,\s*)DPoP\s""")

        val DOCUMENT = mapOf("theme" to "dark", "language" to "fr", "time_zone" to "Europe/Paris")
    }
}
