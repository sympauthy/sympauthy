package com.sympauthy.it.feature

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.JSONObjectUtils
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
 * Feature scenario — **an administrator keeps the admin console's preferences on their own account, in the
 * `sympauthy_admin_preferences` claim the `admin` environment declares.**
 *
 * The container runs the `admin` environment and declares no claim of its own beyond the email the
 * sign-up takes, so every property asserted here is the shipped declaration's. The first administrator
 * signs up through the bootstrap invitation and exchanges the code beside a DPoP proof. With that bound
 * token they write a document through `PATCH /api/v1/user/claims` and read it back through `GET`, are
 * refused a document over 1 KiB and a JSON array, and read the document unchanged afterwards. The same
 * token reads `/userinfo`, whose person door it opens, and is answered without the claim, which the
 * discovery document does not list either. Signing in again without a proof yields a bearer token, whose
 * write is refused with `authentication.unbound_token` on a `DPoP` challenge and whose read answers the
 * claims without the document. A person signing up through a client of the `default` audience is refused
 * the claim with a bound token of their own.
 *
 * The shipped file is what is proved, and only a booted server reads it: its claim beside the keys a
 * deployment writes, its doors against a token the server bound itself, and its publication where the
 * doors would let it through.
 *
 * Issue: [#694](https://github.com/sympauthy/sympauthy/issues/694).
 */
@Tag("feature")
class AdminPreferencesFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "an administrator keeps the console preferences on their account on {0}")
    @EnumSource(Database::class)
    fun keepsAnAdministratorsPreferences(database: Database) {
        withAdminAndApp(database) { sympauthy, admin, app ->
            val key = ECKeyGenerator(Curve.P_256).generate()
            val boundToken = signUpFirstAdmin(sympauthy, admin, ADMIN_EMAIL, key)

            val written = patchClaims(sympauthy, key, boundToken, mapOf(CLAIM to DOCUMENT))
            assertEquals(200, written.statusCode(), "the bound write should be served, body=${written.body()}")
            assertEquals(DOCUMENT, claimsOf(written)[CLAIM], "the write answers the document as an object")

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

            val read = getWithProof(key, boundToken, userClaimsUrl(sympauthy))
            assertEquals(200, read.statusCode(), "the bound read should be served, body=${read.body()}")
            assertEquals(DOCUMENT, claimsOf(read)[CLAIM], "the bound read answers the document the refusals left")

            val userInfoEndpoint = requireNotNull(discovery(sympauthy).userinfoEndpoint)
            val userInfo = getWithProof(key, boundToken, userInfoEndpoint)
            assertEquals(200, userInfo.statusCode(), "/userinfo should serve the bound token, body=${userInfo.body()}")
            assertFalse(
                JSONObjectUtils.parse(userInfo.body()).containsKey(CLAIM),
                "/userinfo should leave the document out, body=${userInfo.body()}",
            )
            assertFalse(
                discovery(sympauthy).claimsSupported.orEmpty().contains(CLAIM),
                "the discovery document should not list the claim",
            )

            val bearerToken = exchange(
                sympauthy, admin,
                admin.newFlow().withSignInHandler { Credentials.of(ADMIN_EMAIL, DEFAULT_PASSWORD) }.run(),
            ).accessToken
            withApiClient(sympauthy, token = bearerToken) { ctx ->
                val refusal = assertThrows<HttpClientResponseException>("a bearer write should be refused") {
                    ctx.getBean(UserApi::class.java).updateClaims(mutableMapOf(CLAIM to DOCUMENT)).block()
                }
                assertError(refusal, 401, "authentication.unbound_token", "a bearer write should be refused")
                val challenge = refusal.response.headers.get("WWW-Authenticate").orEmpty()
                assertTrue(
                    challenge.contains("""DPoP realm="sympauthy", error="invalid_token""""),
                    "the refusal should challenge for a bound token, was: $challenge",
                )

                val readByBearer = requireNotNull(ctx.getBean(UserApi::class.java).getClaims().block()) {
                    "a bearer read returned an empty body"
                }
                assertFalse(
                    readByBearer.claims.containsKey(CLAIM),
                    "a bearer read should leave the document out, was: ${readByBearer.claims}",
                )
            }

            val appKey = ECKeyGenerator(Curve.P_256).generate()
            val appToken = exchangeForBoundToken(sympauthy, app, signUp(app, APP_EMAIL), appKey)
            assertError(
                patchClaims(sympauthy, appKey, appToken, mapOf(CLAIM to DOCUMENT)),
                400, "user.invalid_claim",
                "a client of another audience should be refused the claim",
            )
        }
    }


    /**
     * A `GET` of [url] under the `DPoP` scheme, with [token] and a proof signed by [key]: the generated
     * client presents a token as a bearer one alone.
     */
    private fun getWithProof(key: ECKey, token: String, url: String): HttpResponse<String> =
        httpGet(url, headers = dpopHeaders(key, "GET", url, token))

    /** `PATCH /api/v1/user/claims` with [claims] as its body, as [getWithProof] presents [token]. */
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
     * Starts a container running the `admin` environment, holding the admin client of [admin] and the
     * public client of [app] in the `default` audience, and runs [block] against it.
     */
    private fun withAdminAndApp(
        database: Database,
        block: (SympauthyContainer, InteractiveFlowRegistry, InteractiveFlowRegistry) -> Unit,
    ) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.publicClient(ADMIN_CLIENT_ID)).use { admin ->
                InteractiveFlowRegistry.forClient(Client.publicClient(APP_CLIENT_ID))
                    .withFlowId(APP_FLOW_ID)
                    .use { app ->
                        container(fixture, config(app), admin, app)
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
        const val ADMIN_EMAIL = "administrator@example.com"
        const val APP_CLIENT_ID = "admin-preferences-app"
        const val APP_FLOW_ID = "admin-preferences-flow"
        const val APP_EMAIL = "admin-preferences@example.com"

        val DOCUMENT = mapOf("theme" to "dark", "language" to "fr", "time_zone" to "Europe/Paris")
    }
}
