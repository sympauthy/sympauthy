package com.sympauthy.it.security

import com.sympauthy.api.client.api.ClientApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.SympauthyImage
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
 * Security scenario — **a client must not be able to write a claim restricted to another audience.**
 *
 * Risk: `claims.<id>.audience` decides who a claim is published to, and a restriction enforced only on the way
 * out is half a restriction. A client of `default` holding `users:claims:write` is told nothing about a claim
 * restricted to `billing` — and if it may nonetheless set one, it chooses what billing's applications read
 * about a person while never being accountable for the value. The write side has to ask the same question as
 * the read side.
 *
 * Two audiences are configured and the confidential client belongs to `default`. `custom_region` is restricted
 * to no audience and `custom_tier` to `billing`; both are writable with `users:claims:write`, so the
 * restriction is the only thing that can tell them apart. The scenario signs a person up through the flow,
 * mints a `client_credentials` token, and asserts `PATCH /api/v1/client/users/{userId}/claims` stores
 * `custom_region` and refuses `custom_tier` with `400 client.invalid_claim`, on each supported database.
 *
 * Issue: [#454](https://github.com/sympauthy/sympauthy/issues/454).
 */
@Tag("security")
class ClaimWriteBoundToAudienceIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a client cannot write a claim of another audience on {0}")
    @EnumSource(Database::class)
    fun clientCannotWriteAClaimOfAnotherAudience(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(OWN_CLIENT_ID, CLIENT_SECRET),
            build = { fixture, registry ->
                fixture.applyTo(
                    SympauthyContainer(SympauthyImage.resolve())
                        .withConfig(twoAudienceClientConfig(registry))
                        .withFlows(registry),
                )
            },
        ) { sympauthy, registry ->
            val userId = signUpAndReadSubject(sympauthy, registry)
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:claims:write", "users:claims:read")

            val stored = withApiClient(sympauthy, token = callerToken) { ctx ->
                ctx.getBean(ClientApi::class.java)
                    .updateUserClaims(userId, mutableMapOf("custom_region" to REGION))
                    .block()
            }
            requireNotNull(stored) { "updating a claim of the client's own audience returned an empty body" }
            assertEquals(
                REGION,
                stored.claims["custom_region"],
                "a claim restricted to no audience is the client's to write, was: ${stored.claims}",
            )

            val rejection = claimWriteRejection(sympauthy, callerToken, userId, "custom_tier" to TIER)

            assertEquals(
                400,
                rejection.status,
                "writing a claim restricted to 'billing' from 'default' must be refused, body=${rejection.body}",
            )
            assertTrue(
                rejection.body.contains("client.invalid_claim"),
                "the refusal should name the claim the caller may not write, body=${rejection.body}",
            )
        }
    }

    /** Signs a person up through the flow and returns the subject of the `id_token` it issued — their user id. */
    private fun signUpAndReadSubject(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
    ): UUID {
        val tokens = registry.newFlow()
            .withSignUpHandler { mapOf("email" to EMAIL, "password" to PASSWORD) }
            .run()
            .exchange()
        val idToken = requireNotNull(tokens.idToken()) { "the openid scope should yield an id_token" }
        return UUID.fromString(verifyIdTokenSignature(sympauthy, idToken).subject)
    }

    /**
     * Calls `PATCH /api/v1/client/users/{userId}/claims` expecting a rejection, and captures the HTTP status
     * and error body **inside** the client's context, whose response buffer is released once it closes.
     */
    private fun claimWriteRejection(
        sympauthy: SympauthyContainer,
        callerToken: String,
        userId: UUID,
        claim: Pair<String, Any>,
    ): RejectedWrite = withApiClient(sympauthy, token = callerToken) { ctx ->
        try {
            ctx.getBean(ClientApi::class.java).updateUserClaims(userId, mutableMapOf(claim)).block()
            fail("expected the claim write to be rejected, but it succeeded")
        } catch (error: HttpClientResponseException) {
            RejectedWrite(error.status.code, error.response.getBody(String::class.java).orElse(""))
        }
    }

    /** The HTTP status and raw error body (carrying the server's `error_code`) of a rejected claim write. */
    private data class RejectedWrite(val status: Int, val body: String)

    private companion object {

        const val OWN_CLIENT_ID = "claim-write-app"

        const val CLIENT_SECRET = "s3cr3t-claims"
        const val EMAIL = "claim-write@example.com"
        const val PASSWORD = "Str0ngP@ssw0rd!"
        const val REGION = "eu-west"
        const val TIER = "gold"

        /**
         * Password auth with an email identifier, a second audience beside the shipped `default`, and two
         * custom claims a client may write with `users:claims:write` — `custom_tier` restricted to `billing`,
         * `custom_region` to nothing. Both take that write permission from `templates.claims.default`, so the
         * audience is the only thing separating them.
         *
         * The confidential client names no audience, so it takes `default` from `templates.clients.default`,
         * and holds both the `authorization_code` grant (to sign the person up) and `client_credentials` (to
         * call the client API). `features.grant-unhandled-scopes` is what lets that grant actually hand out
         * the claim scopes, there being no granting rule for them.
         */
        fun twoAudienceClientConfig(registry: InteractiveFlowRegistry): Map<String, Any> = mapOf(
            "audiences" to mapOf(
                "billing" to mapOf("token-audience" to "https://billing.example.com"),
            ),
            "auth" to mapOf(
                "by-password" to mapOf("enabled" to true),
                "identifier-claims" to listOf("email"),
            ),
            "features" to mapOf("grant-unhandled-scopes" to true),
            "claims" to mapOf(
                "email" to mapOf("enabled" to true),
                "custom_region" to mapOf("enabled" to true, "type" to "string"),
                "custom_tier" to mapOf("enabled" to true, "type" to "string", "audience" to "billing"),
            ),
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
    }
}
