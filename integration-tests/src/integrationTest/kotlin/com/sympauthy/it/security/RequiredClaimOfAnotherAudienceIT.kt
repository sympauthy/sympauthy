package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
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
        withCustomContainer(
            database,
            client = Client.publicClient(OWN_CLIENT_ID),
            scopes = SCOPES,
            build = { fixture, registry ->
                registry.withFlowId(OWN_FLOW_ID)
                container(fixture, otherAudienceRequiresAClaimConfig(registry), registry)
            },
        ) { _, registry -> asksForNoClaimOfTheOtherAudience(registry) }
    }

    private fun asksForNoClaimOfTheOtherAudience(registry: InteractiveFlowRegistry) {
        val flow = registry.newFlow()
            .withSignUpHandler { credentials(EMAIL) }

        val tokens = flow.run().exchange()

        assertEquals(
            listOf(FlowStep.Type.SIGN_UP, FlowStep.Type.COMPLETED),
            flow.stepTypes(),
            "a claim only 'billing' requires must add no step to a 'default' sign-up",
        )
        assertNotNull(tokens.accessToken(), "the sign-up should have issued tokens")
    }

    /**
     * Password auth with an email identifier, a second audience beside the shipped `default`, and one
     * required claim that belongs to it alone. The public client names no audience, so it takes `default`
     * from `templates.clients.default`.
     */
    private fun otherAudienceRequiresAClaimConfig(registry: InteractiveFlowRegistry): Map<String, Any> =
        passwordAuthConfig(
            claims = mapOf(
                "nickname" to mapOf("enabled" to true, "required" to true, "audience" to "billing"),
            ),
        ) + mapOf(
            "audiences" to mapOf(
                "billing" to mapOf("token-audience" to "https://billing.example.com"),
            ),
            "clients" to mapOf(
                registry.clientId() to publicClientConfig(registry, scopes = SCOPES),
            ),
        )

    private companion object {

        const val OWN_CLIENT_ID = "required-claim-app"
        const val OWN_FLOW_ID = "required-claim-flow"

        const val EMAIL = "required-claim@example.com"

        val SCOPES = listOf("openid", "profile")
    }
}
