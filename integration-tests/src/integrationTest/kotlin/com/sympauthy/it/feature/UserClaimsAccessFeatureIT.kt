package com.sympauthy.it.feature

import com.sympauthy.api.client.api.UserApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.SympauthyContainer
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **a public client reads and writes the claims of the person signed in to it with their own
 * access token, and writes only the claims the deployment opened to that token.**
 *
 * `name` opens its person write door on consent to `profile`; `given_name` opens it on consent to
 * `profile:write`, a scope the client is allowed and the person is never asked for; `nickname` keeps the ACL the shipped file gives it,
 * which lets the flow collect it on the same consent and opens no door to a token. A person signs up through a
 * public client — no secret anywhere — consenting to `profile` and supplying `name` and `nickname`, and the
 * access token the code exchange issues them is presented to `/api/v1/user/claims`.
 *
 * The write of `name` must be stored and read back by the `GET` beside it. The write of `given_name` must be
 * refused with `403` and a `Bearer` challenge carrying `insufficient_scope` for `profile:write`, which is what
 * a client re-authorizes for. The write of `nickname` must be refused with `400 user.invalid_claim`, and carry
 * no challenge, since no token would be let through: the flow collecting a claim is no permission to write it
 * through an API, which is what keeps every deployment upgrading with the shipped claims exactly as writable
 * as it was before. Neither refusal may change a value.
 *
 * It takes the whole instance because the token is the one a public client obtains through the flow, and the
 * consent the doors ask about is the one that flow recorded.
 *
 * Issue: [#482](https://github.com/sympauthy/sympauthy/issues/482), and `docs/design/claims.md`.
 */
@Tag("feature")
class UserClaimsAccessFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a person writes a claim opened to their token and no other on {0}")
    @EnumSource(Database::class)
    fun writesOnlyTheClaimsOpenedToThePersonsToken(database: Database) {
        withContainer(database, extraConfig = userClaimsConfig(), scopes = SCOPES) { sympauthy, registry ->
            val signedUp = registry.newFlow()
                .withSignUpHandler { credentials(EMAIL) }
                .withClaimsHandler { requested ->
                    requested.mapNotNull { claim -> COLLECTED[claim.id()]?.let { claim.id() to it } }.toMap()
                }
                .run()
            val accessToken = exchange(sympauthy, registry, signedUp).accessToken

            val written = withApiClient(sympauthy, token = accessToken) { ctx ->
                ctx.getBean(UserApi::class.java).updateClaims(mutableMapOf("name" to RENAMED)).block()
            }
            requireNotNull(written) { "updating a claim opened to the person's token returned an empty body" }
            assertEquals(RENAMED, written.claims["name"], "the write answers the stored value, was: ${written.claims}")

            val unconsented = writeRejection(sympauthy, accessToken, "given_name" to GIVEN_NAME)
            assertEquals(403, unconsented.status, "a scope not consented to is a 403, body=${unconsented.body}")
            assertTrue(
                unconsented.challenge.contains(
                    """Bearer realm="sympauthy", error="insufficient_scope", scope="profile:write""""
                ),
                "the refusal should challenge for the scope to re-authorize with, was: ${unconsented.challenge}",
            )

            val shut = writeRejection(sympauthy, accessToken, "nickname" to "Renamed")
            assertEquals(400, shut.status, "a claim the flow collects is not opened to a token, body=${shut.body}")
            assertTrue(
                shut.body.contains("user.invalid_claim"),
                "the refusal should name the claim the person may not write, body=${shut.body}",
            )
            assertFalse(
                shut.challenge.contains("error="),
                "a refusal no token gets past should challenge for nothing, was: ${shut.challenge}",
            )

            val read = withApiClient(sympauthy, token = accessToken) { ctx ->
                ctx.getBean(UserApi::class.java).getClaims().block()
            }
            requireNotNull(read) { "reading the person's claims returned an empty body" }
            assertEquals(RENAMED, read.claims["name"], "the read answers the value written")
            assertEquals(NICKNAME, read.claims["nickname"], "the refused write left the collected value")
            assertFalse("given_name" in read.claims, "the refused write stored nothing, was: ${read.claims}")
        }
    }

    /**
     * Calls `PATCH /api/v1/user/claims` with [claim] expecting a rejection, and captures the HTTP status, the
     * `WWW-Authenticate` header and the error body **inside** the client's context, whose response buffer is
     * released once it closes.
     */
    private fun writeRejection(
        sympauthy: SympauthyContainer,
        accessToken: String,
        claim: Pair<String, Any>,
    ): RejectedWrite = withApiClient(sympauthy, token = accessToken) { ctx ->
        try {
            ctx.getBean(UserApi::class.java).updateClaims(mutableMapOf(claim)).block()
            fail("expected the claim write to be rejected, but it succeeded")
        } catch (error: HttpClientResponseException) {
            RejectedWrite(
                status = error.status.code,
                challenge = error.response.headers.get("WWW-Authenticate").orEmpty(),
                body = error.response.getBody(String::class.java).orElse(""),
            )
        }
    }

    /** What a rejected claim write was answered with: its status, its challenge and its raw error body. */
    private data class RejectedWrite(val status: Int, val challenge: String, val body: String)

    /**
     * `name` and `nickname` turned on and required, so the sign-up collects them, and `given_name` turned on;
     * `name` opens the person's write door on `profile`, and `given_name` on `profile:write`, a consentable
     * scope the client never asks for. Everything else about the three — the `personal` template, the flow's
     * and the read doors on `profile` — is what the shipped file gives them. The client is defaulted to
     * `profile`, which the base configuration does not allow it, and is allowed `profile:write` as well: a
     * scope it could never be issued would be refused by name rather than challenged for.
     */
    private fun userClaimsConfig(): Map<String, Any> = mapOf(
        "scopes" to mapOf(
            WRITE_SCOPE to mapOf("enabled" to true, "type" to "consentable"),
        ),
        "claims" to mapOf(
            "name" to mapOf(
                "enabled" to true,
                "required" to true,
                "acl" to mapOf("writable-by-person" to """SCOPE_IS_CONSENTED("profile")"""),
            ),
            "given_name" to mapOf(
                "enabled" to true,
                "acl" to mapOf("writable-by-person" to """SCOPE_IS_CONSENTED("$WRITE_SCOPE")"""),
            ),
            "nickname" to mapOf("enabled" to true, "required" to true),
        ),
        "clients" to mapOf(
            clientId to mapOf("allowed-scopes" to SCOPES + WRITE_SCOPE, "default-scopes" to SCOPES),
        ),
    )

    private companion object {

        const val EMAIL = "user-claims-access@example.com"
        const val FULL_NAME = "Ada Lovelace"
        const val RENAMED = "Ada King"
        const val GIVEN_NAME = "Augusta"
        const val NICKNAME = "Ada"
        const val WRITE_SCOPE = "profile:write"

        val SCOPES = listOf("openid", "profile")

        /**
         * The value the sign-up collects for each claim its flow asks for, by claim id. `given_name` is left
         * out, so a value read back for it could only have come from the refused write.
         */
        val COLLECTED: Map<String, String> = mapOf(
            "email" to EMAIL,
            "name" to FULL_NAME,
            "nickname" to NICKNAME,
        )
    }
}
