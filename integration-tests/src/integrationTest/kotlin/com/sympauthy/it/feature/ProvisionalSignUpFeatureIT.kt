package com.sympauthy.it.feature

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.AuthorizationResult
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.FlowStep
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **an account being signed up does not exist until the flow completes.**
 *
 * Two sign-ups race for one email address. The first is paused part-way through, on the collect-claims step
 * that follows its sign-up: at that moment its account, its password and its email claim are all written, and
 * the whole point of the feature is that none of it counts yet. The second sign-up then runs to completion
 * with the same address — which it could only do if the first one's rows were invisible to the duplicate
 * check — and takes the address. The first is finally resumed, and fails, because the address it thought it
 * had was claimed by whoever finished first.
 *
 * That is both halves of the design in one scenario: a provisional account is invisible to the query that
 * resolves an identifier, and the collision it makes possible is settled when the account is promoted rather
 * than when it is written. Signing in afterwards proves exactly one account came out of it.
 *
 * The pause is the first flow's claims handler, which the driver calls while the server waits for the
 * collect-claims step. The second sign-up runs there, against a **second client with its own mock frontend**:
 * a registry drives one flow at a time, so the racing flow needs its own.
 *
 * `name` is what makes the pause: it is required, so a sign-up is followed by a collect-claims step. Being a
 * `profile` claim, both clients must be allowed that scope and both flows must request it — a required claim
 * outside the consented scopes is not collected, and no step would appear.
 *
 * Issue: [#281](https://github.com/sympauthy/sympauthy/issues/281).
 */
@Tag("feature")
class ProvisionalSignUpFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a sign-up counts only once it completes on {0}")
    @EnumSource(Database::class)
    fun signUpCountsOnlyOnceItCompletes(database: Database) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.publicClient(clientId))
                .withScopes(*SCOPES).use { first ->
                    InteractiveFlowRegistry.forClient(Client.publicClient(RACING_CLIENT_ID))
                        .withFlowId("racing").withScopes(*SCOPES).use { racing ->
                            fixture.applyTo(
                                SympauthyContainer(SympauthyImage.resolve())
                                    .withConfig(twoClientSignUpConfig(first, racing))
                                    .withFlows(first)
                                    .withFlows(racing),
                            ).use { sympauthy ->
                                withStartedContainer(sympauthy) { raceForOneAddress(first, racing) }
                            }
                        }
                }
        }
    }

    private fun raceForOneAddress(registry: InteractiveFlowRegistry, racing: InteractiveFlowRegistry) {
        var racingSignUp: AuthorizationResult? = null

        val paused = registry.newFlow()
            .withSignUpHandler { mapOf("email" to EMAIL, "password" to FIRST_PASSWORD) }
            .withClaimsHandler { requested ->
                // The first account is written and provisional. A second sign-up for the same address is let
                // through — the duplicate check cannot see it — and finishes first.
                racingSignUp = racing.newFlow()
                    .withSignUpHandler { mapOf("email" to EMAIL, "password" to RACING_PASSWORD) }
                    .withClaimsHandler { nested -> nested.associate { it.id() to NAME } }
                    .run()
                requested.associate { it.id() to NAME }
            }

        assertThrows<RuntimeException>("the first sign-up must not complete once the address is taken") {
            paused.run()
        }

        val winner = requireNotNull(racingSignUp) { "the racing sign-up should have completed" }
        assertNotNull(winner.exchange().accessToken(), "the racing sign-up should have issued tokens")
        assertEquals(
            listOf(FlowStep.Type.SIGN_UP, FlowStep.Type.CLAIMS),
            paused.stepTypes(),
            "the first flow walked its steps and never reached completion",
        )

        // Exactly one account holds the address, and it is the one that finished: its password signs in.
        val signedIn = racing.newFlow()
            .withSignInHandler { Credentials.of(EMAIL, RACING_PASSWORD) }
            .run()

        assertNotNull(signedIn.exchange().accessToken(), "signing in with the winner's password")
    }

    private companion object {

        const val RACING_CLIENT_ID = "racing-app"
        const val EMAIL = "provisional@example.com"
        const val FIRST_PASSWORD = "F1rstP@ssw0rd!"
        const val RACING_PASSWORD = "S3condP@ssw0rd!"
        const val NAME = "Ada Lovelace"

        val SCOPES = arrayOf("openid", "profile")

        /**
         * Password auth with an email identifier and a required `name` claim, and the two public clients the
         * racing sign-ups come through, each owning its own flow and allowed the `profile` scope.
         */
        fun twoClientSignUpConfig(
            first: InteractiveFlowRegistry,
            racing: InteractiveFlowRegistry,
        ): Map<String, Any> = mapOf(
            "auth" to mapOf(
                "by-password" to mapOf("enabled" to true),
                "identifier-claims" to listOf("email"),
            ),
            "claims" to mapOf(
                "email" to mapOf("enabled" to true),
                "name" to mapOf("enabled" to true, "required" to true),
            ),
            "clients" to mapOf(
                first.clientId() to publicClientConfig(first),
                racing.clientId() to publicClientConfig(racing),
            ),
        )

        fun publicClientConfig(registry: InteractiveFlowRegistry): Map<String, Any> = mapOf(
            "public" to true,
            "authorizationFlow" to registry.flowId(),
            "allowed-grant-types" to listOf("authorization_code"),
            "allowed-scopes" to SCOPES.toList(),
            "allowed-redirect-uris" to listOf(registry.redirectUri()),
        )
    }
}
