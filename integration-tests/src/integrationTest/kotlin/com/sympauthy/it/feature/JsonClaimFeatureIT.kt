package com.sympauthy.it.feature

import com.sympauthy.api.client.api.ClientApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
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
 * Feature scenario — **a deployment keeps a document about a person in a claim of type `json`.**
 *
 * `preferences` is a `json` claim an application answers for, held to a `max-size` of 64 bytes and
 * published in the id token. A person signs up, and a confidential client writes a document for them
 * through `PATCH /api/v1/client/users/{userId}/claims` and reads the same document back through the
 * `GET` beside it. The same client is then refused a JSON array, a string holding the text of an
 * object, and a document over the size — each with the code naming why — and the stored document is
 * left as it was. The person signs in again, and the id token issued then carries the document as the
 * nested object it is.
 *
 * No unit test reaches this: what is proved is that a document survives the request body, the column,
 * the response body and the JWT on a real server and database, each of which serializes it its own way.
 *
 * Issue: [#483](https://github.com/sympauthy/sympauthy/issues/483), and `docs/design/claims.md`.
 */
@Tag("feature")
class JsonClaimFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a client writes and reads back a document, published in the id token, on {0}")
    @EnumSource(Database::class)
    fun keepsADocumentAboutAPerson(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(CLIENT_ID, CLIENT_SECRET),
            build = { fixture, registry -> container(fixture, jsonClaimConfig(registry), registry) },
        ) { sympauthy, registry ->
            val userId = UUID.fromString(subjectOf(sympauthy, signUpAndExchange(sympauthy, registry, EMAIL).idToken))
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:claims:write", "users:claims:read")

            val stored = withApiClient(sympauthy, token = callerToken) { ctx ->
                ctx.getBean(ClientApi::class.java)
                    .updateUserClaims(userId, mutableMapOf("preferences" to DOCUMENT))
                    .block()
            }
            assertEquals(DOCUMENT, stored?.claims?.get("preferences"), "the write answers the stored document")

            assertRefused(sympauthy, callerToken, userId, listOf(1, 2), "user.claim_value_validator.invalid_json")
            assertRefused(
                sympauthy, callerToken, userId, """{"theme": "light"}""", "user.claim_value_validator.invalid_json"
            )
            assertRefused(
                sympauthy, callerToken, userId, mapOf("theme" to "x".repeat(64)), "user.claim_value_validator.too_large"
            )

            val read = withApiClient(sympauthy, token = callerToken) { ctx ->
                ctx.getBean(ClientApi::class.java).getUserClaims(userId).block()
            }
            assertEquals(DOCUMENT, read?.claims?.get("preferences"), "a refused write leaves the document as it was")

            val idTokenClaims = verifyIdTokenSignature(sympauthy, signIn(sympauthy, registry, EMAIL))
            assertEquals(
                DOCUMENT,
                idTokenClaims.getJSONObjectClaim("preferences"),
                "the id token carries the document as an object",
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
     * Password auth with an email identifier, the `json` claim, and the confidential client holding the
     * `authorization_code` grant to sign the person in and `client_credentials` to call the client API.
     *
     * The claim is an application's, so the flow never asks for it, and its ACL lets any client read it —
     * which is what lets the id token of a person's own grant carry it — while writing it takes
     * `users:claims:write`. `features.grant-unhandled-scopes` is what hands that scope out, there being no
     * granting rule for it.
     */
    private fun jsonClaimConfig(registry: InteractiveFlowRegistry): Map<String, Any> =
        passwordAuthConfig() and mapOf(
            "claims" to mapOf(
                "preferences" to mapOf(
                    "enabled" to true,
                    "type" to "json",
                    "template" to "application",
                    "max-size" to 64,
                    "published-in" to listOf("id-token"),
                    "acl" to mapOf("readable-by-client" to "true"),
                ),
            ),
            "features" to mapOf("grant-unhandled-scopes" to true),
            "clients" to mapOf(
                registry.clientId() to mapOf(
                    "public" to false,
                    "secret" to CLIENT_SECRET,
                    "authorizationFlow" to registry.flowId(),
                    "allowed-grant-types" to listOf("authorization_code", "client_credentials"),
                    "allowed-scopes" to listOf("openid", "users:claims:read", "users:claims:write"),
                    "default-scopes" to listOf("openid"),
                    "allowed-redirect-uris" to listOf(registry.redirectUri()),
                ),
            ),
        )

    private companion object {

        const val CLIENT_ID = "json-claim-app"
        const val CLIENT_SECRET = "s3cr3t-json-claim"
        const val EMAIL = "json-claim@example.com"

        /** 39 bytes once serialized, under the 64 the claim is held to. */
        val DOCUMENT = mapOf("theme" to "dark", "panels" to listOf("grants", "users"))
    }
}
