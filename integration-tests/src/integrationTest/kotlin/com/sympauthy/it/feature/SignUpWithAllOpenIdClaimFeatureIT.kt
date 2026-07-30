package com.sympauthy.it.feature

import com.nimbusds.jose.util.JSONObjectUtils
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.FlowStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **sign up while collecting every standard OpenID claim, then verify each stored value.**
 *
 * A user signs up through the interactive flow with every standard OpenID claim enabled and required: the
 * sign-up step takes the identifier + password, a single collect-claims step gathers all the remaining
 * OpenID claims, and the flow completes and issues tokens. Every collected claim is then read back and
 * asserted against the value it was signed up with. This proves end-to-end sign-up, multi-scope consent, the
 * collect-claims step with per-type claim validation, and claim persistence all work on the running native
 * image, against each supported database.
 *
 * The claims are read back through the admin user API (`GET /api/v1/admin/users/{userId}/claims`) — the most
 * direct way to see every stored claim. Reaching that API needs an access token carrying the admin scopes, so
 * the sign-up runs in the `admin` environment ([SympauthyContainer.withAdmin]) and redeems its `first-admin`
 * bootstrap invitation: the invitation marks the new user `is_sympauthy_admin=true`, a scope-granting rule
 * turns that into the admin scopes, and the flow's own access token can then call the admin API. The
 * invitation is only the means to that assertion, not the subject of the test.
 *
 * Reference: [OpenID Connect Core 1.0, Standard Claims](https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims).
 */
@Tag("feature")
class SignUpWithAllOpenIdClaimFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "sign-up collects and verifies all OpenID claims on {0}")
    @EnumSource(Database::class)
    fun signsUpCollectingAllOpenIdClaims(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry ->
                fixture.applyTo(
                    SympauthyContainer(SympauthyImage.resolve())
                        .withAdmin()
                        .withConfig(passwordAndAllClaimsConfig())
                        .withFlows(registry)
                        .withAdminClient(registry, "profile", "email", "phone", "address", "admin:users:read"),
                )
            },
        ) { sympauthy, registry ->
            // Redeem the first-admin bootstrap invitation: sign up (identifier + password), then a single
            // collect-claims step gathers every other required OpenID claim.
            val invitationToken = sympauthy.getBootstrapInvitationToken("first-admin")
            val flow = registry.newFlow()
                .withInvitationToken(invitationToken)
                .withSignUpHandler { mapOf("email" to EXPECTED.getValue("email"), "password" to PASSWORD) }
                .withClaimsHandler { requested -> requested.associate { it.id() to EXPECTED.getValue(it.id()) } }

            val result = flow.run()

            assertEquals(
                listOf(FlowStep.Type.SIGN_UP, FlowStep.Type.CLAIMS, FlowStep.Type.COMPLETED),
                flow.stepTypes(),
                "invitation routes to sign-up, then one collect-claims step, then completion",
            )

            val tokens = result.exchange()
            assertNotNull(tokens.accessToken(), "token response should carry an access token")
            assertTrue(
                tokens.scope()?.split(" ")?.contains("admin:users:read") == true,
                "the is_sympauthy_admin rule should grant the admin read scope, was: ${tokens.scope()}",
            )

            // The id_token subject is the user id; verifying it also proves the token is genuinely signed.
            val idToken = requireNotNull(tokens.idToken()) { "the openid scope should yield an id_token" }
            val userId = verifyIdTokenSignature(sympauthy, idToken).subject

            // Read every collected claim back through the admin API using the token the flow just issued.
            val response = httpGet(
                "${sympauthy.baseUrl}/api/v1/admin/users/$userId/claims?size=100",
                headers = mapOf("Authorization" to "Bearer ${tokens.accessToken()}"),
            )
            assertEquals(
                200, response.statusCode(),
                "admin claims API should authorize the granted token: ${response.body()}",
            )

            val values = (JSONObjectUtils.parse(response.body())["claims"] as List<*>)
                .filterIsInstance<Map<String, Any?>>()
                .associate { it["claim_id"] as String to it["value"] }

            EXPECTED.forEach { (id, value) ->
                assertEquals(value, values[id], "claim '$id' read back through the admin user API")
            }
            assertEquals(userId, values["sub"], "the generated 'sub' claim should equal the user id")
            assertEquals(
                "true", values["is_sympauthy_admin"],
                "the invitation should have marked the user as an admin",
            )
        }
    }

    private companion object {

        const val PASSWORD = "Str0ngP@ssw0rd!"

        /**
         * The value signed up for each standard OpenID claim, by claim id. Each value is valid for its
         * claim's data type (see `ClaimValueValidator`): dates as `yyyy-MM-dd`, phone numbers as `+`
         * followed by digits, `zoneinfo` an IANA zone id, `email` an `x@y` address, the rest plain strings.
         * The keys also drive [passwordAndAllClaimsConfig] — enabling exactly the claims asserted here.
         */
        val EXPECTED: Map<String, String> = linkedMapOf(
            "name" to "Ada Lovelace",
            "given_name" to "Ada",
            "family_name" to "Lovelace",
            "middle_name" to "Augusta",
            "nickname" to "Ada",
            "preferred_username" to "ada.lovelace",
            "profile" to "https://example.com/ada",
            "picture" to "https://example.com/ada.png",
            "website" to "https://ada.example.com",
            "gender" to "female",
            "birth_date" to "1815-12-10",
            "zoneinfo" to "Europe/London",
            "locale" to "en-GB",
            "email" to "ada.lovelace@example.com",
            "phone_number" to "+14155550100",
            "street_address" to "12 Analytical Engine Way",
            "locality" to "London",
            "region" to "Greater London",
            "postal_code" to "EC1A 1BB",
            "country" to "GB",
        )

        /**
         * Password auth with an email identifier, plus every standard OpenID claim enabled and required so
         * the flow gathers them all. Claim keys mirror the underlying claim ids (see [EXPECTED]); the
         * generated `sub` / `updated_at` claims are always on and need no configuration.
         */
        fun passwordAndAllClaimsConfig(): Map<String, Any> {
            val enabledRequired = mapOf("enabled" to true, "required" to true)
            return mapOf(
                "auth" to mapOf(
                    "by-password" to mapOf("enabled" to true),
                    "identifier-claims" to listOf("email"),
                ),
                "claims" to EXPECTED.keys.associateWith { enabledRequired },
            )
        }
    }
}
