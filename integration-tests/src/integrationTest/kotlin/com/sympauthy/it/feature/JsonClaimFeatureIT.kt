package com.sympauthy.it.feature

import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.api.client.api.AdminApi
import com.sympauthy.api.client.api.ClientApi
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

/**
 * Feature scenario — **a deployment keeps a document about a person in a claim of type `json`, and every
 * place that answers the claim answers it as the object it is.**
 *
 * `preferences` is a `json` claim held to a `max-size` of 64 bytes and published in every place that
 * carries a value: the id token, `/userinfo`, the access token and the introspection response. It is the
 * person's, restricted to the audience of the confidential client, so both the person's own token and the
 * client may write it. A person signs up, and the client writes a document for them through
 * `PATCH /api/v1/client/users/{userId}/claims`. The same client is then refused a JSON array, a string
 * holding the text of an object, and a document over the size — each with the code naming why — and the
 * stored document is left as it was, which the person reads through `GET /api/v1/user/claims`. The person
 * replaces it through the `PATCH` beside that read, and the client reads the replacement back, as does an
 * administrator through `GET /api/v1/admin/users/{userId}/claims`. The person signs in again, and the id
 * token, the access token, the `/userinfo` read with that access token and its introspection by the client
 * each carry the replacement as a nested object.
 *
 * No unit test reaches this: what is proved is that a document survives each request body, the column,
 * each response body and each JWT on a real server and database, every one of which serializes it its own
 * way.
 *
 * Issue: [#483](https://github.com/sympauthy/sympauthy/issues/483), and `docs/design/claims.md`.
 */
@Tag("feature")
class JsonClaimFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a document is answered as an object by every place publishing it on {0}")
    @EnumSource(Database::class)
    fun keepsADocumentAboutAPerson(database: Database) {
        withAdminAndApp(database) { sympauthy, admin, app ->
            val adminToken = signUpFirstAdmin(sympauthy, admin)
            val signedUp = signUpAndExchange(sympauthy, app, EMAIL)
            val userId = UUID.fromString(subjectOf(sympauthy, signedUp.idToken))
            val callerToken = clientCredentialsToken(sympauthy, app, "users:claims:write", "users:claims:read")

            val stored = withApiClient(sympauthy, token = callerToken) { ctx ->
                ctx.getBean(ClientApi::class.java)
                    .updateUserClaims(userId, mutableMapOf("preferences" to DOCUMENT))
                    .block()
            }
            assertEquals(DOCUMENT, stored?.claims?.get("preferences"), "the client write answers the document")

            assertRefused(sympauthy, callerToken, userId, listOf(1, 2), "user.claim_value_validator.invalid_json")
            assertRefused(
                sympauthy, callerToken, userId, """{"theme": "light"}""", "user.claim_value_validator.invalid_json"
            )
            assertRefused(
                sympauthy, callerToken, userId, mapOf("theme" to "x".repeat(64)), "user.claim_value_validator.too_large"
            )

            val readByPerson = withApiClient(sympauthy, token = signedUp.accessToken) { ctx ->
                ctx.getBean(UserApi::class.java).getClaims().block()
            }
            assertEquals(
                DOCUMENT,
                readByPerson?.claims?.get("preferences"),
                "the person reads the document a refused write left as it was",
            )

            val rewritten = withApiClient(sympauthy, token = signedUp.accessToken) { ctx ->
                ctx.getBean(UserApi::class.java).updateClaims(mutableMapOf("preferences" to REWRITTEN)).block()
            }
            assertEquals(REWRITTEN, rewritten?.claims?.get("preferences"), "the person write answers the document")

            val readByClient = withApiClient(sympauthy, token = callerToken) { ctx ->
                ctx.getBean(ClientApi::class.java).getUserClaims(userId).block()
            }
            assertEquals(REWRITTEN, readByClient?.claims?.get("preferences"), "the client reads the person write")

            val readByAdmin = withApiClient(sympauthy, token = adminToken) { ctx ->
                ctx.getBean(AdminApi::class.java).listUserClaims(userId, size = 100).block()
            }
            assertEquals(
                REWRITTEN,
                readByAdmin?.claims?.singleOrNull { it.claimId == "preferences" }?.value,
                "an administrator reads the document",
            )

            val signedIn = exchange(
                sympauthy, app,
                app.newFlow().withSignInHandler { Credentials.of(EMAIL, DEFAULT_PASSWORD) }.run(),
            )
            assertEquals(
                REWRITTEN,
                verifyIdTokenSignature(sympauthy, requireIdToken(signedIn.idToken)).getJSONObjectClaim("preferences"),
                "the id token carries the document as an object",
            )
            assertEquals(
                REWRITTEN,
                SignedJWT.parse(signedIn.accessToken).jwtClaimsSet.getJSONObjectClaim("preferences"),
                "the access token carries the document as an object",
            )

            val userInfo = withApiClient(sympauthy, token = signedIn.accessToken) { ctx ->
                ctx.getBean(OpenidApi::class.java).getUserInfo().block()
            }
            assertEquals(REWRITTEN, userInfo?.get("preferences"), "/userinfo carries the document as an object")

            assertEquals(
                REWRITTEN,
                introspectedMembers(sympauthy, app, signedIn.accessToken)["preferences"],
                "the introspection response carries the document as an object",
            )
        }
    }

    /** Writes [value] for `preferences` as the client, and asserts it is refused with `400` and [code]. */
    private fun assertRefused(
        sympauthy: SympauthyContainer,
        callerToken: String,
        userId: UUID,
        value: Any,
        code: String,
    ) {
        // The body is read inside the client's context, whose response buffer is released once it closes.
        val (status, body) = withApiClient(sympauthy, token = callerToken) { ctx ->
            try {
                ctx.getBean(ClientApi::class.java)
                    .updateUserClaims(userId, mutableMapOf("preferences" to value))
                    .block()
                fail("expected writing $value to be refused, but it succeeded")
            } catch (error: HttpClientResponseException) {
                error.status.code to error.response.getBody(String::class.java).orElse("")
            }
        }
        assertEquals(400, status, "writing $value should be refused, body=$body")
        assertTrue(body.contains(code), "the refusal of $value should name $code, body=$body")
    }

    /**
     * The members introspecting [token] as [registry]'s client answers, read off the raw body: the
     * generated model holds the members RFC 7662 defines and drops the claims published beside them.
     */
    private fun introspectedMembers(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        token: String,
    ): Map<String, Any> {
        val response = httpPostForm(
            checkNotNull(discovery(sympauthy).introspectionEndpoint) {
                "the deployment publishes no introspection endpoint"
            },
            mapOf("token" to token),
            basicAuth(registry),
        )
        assertEquals(200, response.statusCode(), "introspection should answer, body=${response.body()}")
        return JSONObjectUtils.parse(response.body())
    }

    /** Redeems the first-admin bootstrap invitation, signs up, and answers the admin's access token. */
    private fun signUpFirstAdmin(sympauthy: SympauthyContainer, registry: InteractiveFlowRegistry): String {
        val invitationToken = sympauthy.getBootstrapInvitationToken("first-admin")
        val signedUp = registry.newFlow()
            .withInvitationToken(invitationToken)
            .withSignUpHandler { credentials(ADMIN_EMAIL) }
            .run()
        return exchange(sympauthy, registry, signedUp).accessToken
    }

    /**
     * Starts a container holding the admin client of [admin], allowed to read every claim of every user,
     * and the confidential client of [app] that the person signs in through, and runs [block] against it.
     */
    private fun withAdminAndApp(
        database: Database,
        block: (SympauthyContainer, InteractiveFlowRegistry, InteractiveFlowRegistry) -> Unit,
    ) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.publicClient(ADMIN_CLIENT_ID)).use { admin ->
                InteractiveFlowRegistry.forClient(Client.confidentialClient(APP_CLIENT_ID, APP_CLIENT_SECRET))
                    .withFlowId(APP_FLOW_ID)
                    .use { app ->
                        container(fixture, config(app) and jsonClaimConfig(app), admin, app)
                            .withAdmin()
                            .withAdminClient(admin, "openid", "admin:users:read")
                            .use { sympauthy -> withStartedContainer(sympauthy) { block(it, admin, app) } }
                    }
            }
        }
    }

    /**
     * The `json` claim, and the confidential client of [app] holding `client_credentials` beside the
     * `authorization_code` grant [config] gives it, to call the client API.
     *
     * The claim is the person's, so its person doors may open, and restricted to `default` — the audience
     * the client takes from `templates.clients.default` — so its client write door may open too. Every
     * door but the client write is the literal `"true"`, which is what lets the person's own grant carry
     * the claim in each place without a scope for it, while the client write takes `users:claims:write`.
     * `features.grant-unhandled-scopes` is what hands that scope out, there being no granting rule for it.
     */
    private fun jsonClaimConfig(app: InteractiveFlowRegistry): Map<String, Any> = mapOf(
        "claims" to mapOf(
            "preferences" to mapOf(
                "enabled" to true,
                "type" to "json",
                "template" to "personal",
                "audience" to "default",
                "max-size" to 64,
                "published-in" to listOf("id-token", "userinfo", "access-token", "introspection"),
                "acl" to mapOf(
                    "readable-by-person" to "true",
                    "writable-by-person" to "true",
                    "readable-by-client" to "true",
                    "writable-by-client" to """SCOPE_IS_GRANTED("users:claims:write")""",
                ),
            ),
        ),
        "features" to mapOf("grant-unhandled-scopes" to true),
        "clients" to mapOf(
            app.clientId() to mapOf(
                "allowed-grant-types" to listOf("authorization_code", "client_credentials"),
                "allowed-scopes" to listOf("openid", "users:claims:read", "users:claims:write"),
            ),
        ),
    )

    private companion object {

        const val ADMIN_CLIENT_ID = "admin-console"
        const val ADMIN_EMAIL = "admin@example.com"
        const val APP_CLIENT_ID = "json-claim-app"
        const val APP_CLIENT_SECRET = "s3cr3t-json-claim"
        const val APP_FLOW_ID = "json-claim-flow"
        const val EMAIL = "json-claim@example.com"

        /** 39 bytes once serialized, under the 64 the claim is held to. */
        val DOCUMENT = mapOf("theme" to "dark", "panels" to listOf("grants", "users"))

        /** Nested a level deeper than [DOCUMENT], and still under the 64 bytes. */
        val REWRITTEN = mapOf("theme" to "light", "layout" to mapOf("density" to "compact"))
    }
}
