package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.FlowStep
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **a claim another audience requires must not hold up a sign-in to this one.**
 *
 * Risk: the restriction in `claims.<id>.audience` decides who a claim is published to, and what a flow requires
 * has to be read through it too. A required set taken across every audience makes one audience's configuration
 * an availability lever on every other: a claim restricted to `billing` and never publishable to `default`
 * would still stop a `default` sign-in until the person filled it in — and filling it in writes the same
 * restricted row, so the step it adds is one nothing it collects can clear.
 *
 * Two audiences are configured and the client belongs to `default`, the one its template names. `nickname` is
 * required and restricted to `billing`, and nothing else is required, so a `default` sign-up owes no claim at
 * all. The scenario asserts the flow walks sign-up straight to completion — no collect-claims step — and
 * issues its tokens, on each supported database. No claims handler is registered, so a flow that did ask
 * fails rather than quietly answering.
 *
 * Issue: [#454](https://github.com/sympauthy/sympauthy/issues/454).
 */
@Tag("security")
class RequiredClaimOfAnotherAudienceIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a required claim of another audience does not hold up a sign-in on {0}")
    @EnumSource(Database::class)
    fun requiredClaimOfAnotherAudienceDoesNotHoldUpASignUp(database: Database) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.publicClient(clientId)).withScopes(*SCOPES).use { registry ->
                fixture.applyTo(
                    SympauthyContainer(SympauthyImage.resolve())
                        .withConfig(otherAudienceRequiresAClaimConfig(registry))
                        .withFlows(registry),
                ).use { sympauthy ->
                    withStartedContainer(sympauthy) { asksForNoClaimOfTheOtherAudience(registry) }
                }
            }
        }
    }

    private fun asksForNoClaimOfTheOtherAudience(registry: InteractiveFlowRegistry) {
        val flow = registry.newFlow()
            .withSignUpHandler { mapOf("email" to EMAIL, "password" to PASSWORD) }

        val tokens = flow.run().exchange()

        assertEquals(
            listOf(FlowStep.Type.SIGN_UP, FlowStep.Type.COMPLETED),
            flow.stepTypes(),
            "a claim only 'billing' requires must add no step to a 'default' sign-up",
        )
        assertNotNull(tokens.accessToken(), "the sign-up should have issued tokens")
    }

    private companion object {

        const val EMAIL = "ada@example.com"
        const val PASSWORD = "Str0ngP@ssw0rd!"

        val SCOPES = arrayOf("openid", "profile")

        /**
         * Password auth with an email identifier, a second audience beside the shipped `default`, and one
         * required claim that belongs to it alone. The public client names no audience, so it takes `default`
         * from `templates.clients.default`.
         */
        fun otherAudienceRequiresAClaimConfig(registry: InteractiveFlowRegistry): Map<String, Any> = mapOf(
            "audiences" to mapOf(
                "billing" to mapOf("token-audience" to "https://billing.example.com"),
            ),
            "auth" to mapOf(
                "by-password" to mapOf("enabled" to true),
                "identifier-claims" to listOf("email"),
            ),
            "claims" to mapOf(
                "email" to mapOf("enabled" to true),
                "nickname" to mapOf("enabled" to true, "required" to true, "audience" to "billing"),
            ),
            "clients" to mapOf(
                registry.clientId() to mapOf(
                    "public" to true,
                    "authorizationFlow" to registry.flowId(),
                    "allowed-grant-types" to listOf("authorization_code"),
                    "allowed-scopes" to SCOPES.toList(),
                    "allowed-redirect-uris" to listOf(registry.redirectUri()),
                ),
            ),
        )
    }
}
