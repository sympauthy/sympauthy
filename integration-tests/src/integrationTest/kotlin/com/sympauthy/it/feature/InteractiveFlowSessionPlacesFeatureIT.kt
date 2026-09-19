package com.sympauthy.it.feature

import com.sympauthy.api.client.api.AdminApi
import com.sympauthy.api.client.model.AdminInteractiveFlowSessionDetailResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.DatabaseFixture
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.FlowOutcome
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

/**
 * Feature scenario — **where an interactive flow session was driven from, as the session accumulates it and
 * as the admin API publishes it.**
 *
 * What is being proved is the sequence: the places are written by one surface, request after request, read
 * back through another, and consumed by the flow's own completion. A session driven from one place for
 * several requests is one entry counting them; a credential proof stamps that entry; completing the flow
 * folds the proven one into the person's record and leaves the session holding none. No double reproduces
 * that — it is three controllers, a scheduled write on every request and a transaction boundary at the end.
 *
 * The second scenario is the bound: the user agent is the caller's to write, so a session may not mint an
 * entry per request — past the bound the place seen least recently makes room for the freshest one, and
 * reaching the bound is never what stops a person signing in.
 *
 * Source: [sympauthy#459](https://github.com/sympauthy/sympauthy/issues/459) — recording where every
 * interaction with an interactive flow was observed from.
 */
@Tag("feature")
class InteractiveFlowSessionPlacesFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a session counts the place it is driven from and gives it up on {0}")
    @EnumSource(Database::class)
    fun countsOnePlaceAndConsumesItWhenTheFlowCompletes(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry -> adminReadingContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val adminToken = signUpFirstAdmin(sympauthy, registry)

            val signInPage = startAuthorization(sympauthy, registry)
            val state = queryParam(signInPage, "state") ?: fail("the redirect carried no state: $signInPage")
            val signInStep = "${sympauthy.baseUrl}/api/v1/flow/sign-in?state=${encode(state)}"
            repeat(2) { assertEquals(200, httpGet(signInStep, headers = agent(AGENT)).statusCode()) }

            val sessionId = ongoingSessionId(sympauthy, adminToken)
            val stalled = session(sympauthy, adminToken, sessionId)
            val place = stalled.securityContexts.singleOrNull()
                ?: fail("a session driven from one place should hold one entry: ${stalled.securityContexts}")
            // The authorize request that created the session, and the two that asked for the sign-in step.
            assertEquals(3, place.observationCount, "the entry should count every request from that place")
            assertEquals(AGENT, place.userAgent, "the entry should carry the agent those requests claimed")
            assertNull(place.provenDate, "nothing has proven a credential on this session yet")

            val result = registry.newFlow()
                .withSignInHandler { Credentials.of(ADMIN_EMAIL, PASSWORD) }
                .driveFrom(signInPage, registry.redirectUri(), registry.redirectUri())
                .drive()
            assertEquals(FlowOutcome.SUCCESS, result.outcome(), "the flow should complete")
            assertNotNull(result.terminalParam("code"), "completing should hand the client a code")

            val completed = session(sympauthy, adminToken, sessionId)
            assertTrue(
                completed.securityContexts.isEmpty(),
                "completing folds the proven place into the person's record and consumes them all, was: " +
                    "${completed.securityContexts}",
            )
        }
    }

    @ParameterizedTest(name = "a flow driven from more places than are held rolls the oldest out on {0}")
    @EnumSource(Database::class)
    fun rollsTheOldestPlaceOutAndStillCompletes(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry -> adminReadingContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val adminToken = signUpFirstAdmin(sympauthy, registry)

            val signInPage = startAuthorization(sympauthy, registry)
            val state = queryParam(signInPage, "state") ?: fail("the redirect carried no state: $signInPage")
            val signInStep = "${sympauthy.baseUrl}/api/v1/flow/sign-in?state=${encode(state)}"
            repeat(PLACES) { place ->
                assertEquals(
                    200, httpGet(signInStep, headers = agent("place-$place/1.0")).statusCode(),
                    "the sign-in step should answer place $place",
                )
            }

            val sessionId = ongoingSessionId(sympauthy, adminToken)
            val places = session(sympauthy, adminToken, sessionId).securityContexts
            val agents = places.map { it.userAgent }
            assertTrue(
                places.size < PLACES,
                "a session may not hold an entry per agent a caller chooses, held: ${places.size}",
            )
            assertTrue(
                agents.contains("place-${PLACES - 1}/1.0"),
                "the place it was last driven from is the one worth keeping, held: $agents",
            )
            assertFalse(
                agents.contains("place-0/1.0"),
                "the place seen least recently is the one that made room, held: $agents",
            )

            val result = registry.newFlow()
                .withSignInHandler { Credentials.of(ADMIN_EMAIL, PASSWORD) }
                .driveFrom(signInPage, registry.redirectUri(), registry.redirectUri())
                .drive()

            assertEquals(FlowOutcome.SUCCESS, result.outcome(), "reaching the bound may not fail the flow")
            assertNotNull(result.terminalParam("code"), "completing should hand the client a code")
        }
    }

    /**
     * Starts an authorization from [AGENT] and answers the page the `303` sends the end-user to, which
     * carries the signed state the flow endpoints are driven with.
     */
    private fun startAuthorization(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
    ): String {
        val authorize = httpGet(authorizeUrl(sympauthy, registry), headers = agent(AGENT))
        assertEquals(303, authorize.statusCode(), "authorize should redirect to the first step")
        return authorize.headers().firstValue("Location").orElseThrow {
            error("authorize 303 had no Location")
        }
    }

    /**
     * The identifier of the one session still ongoing, which is the authorization just started: the admin's
     * own sign-up completed before it.
     */
    private fun ongoingSessionId(sympauthy: SympauthyContainer, token: String): UUID {
        val listed = withApiClient(sympauthy, token = token) { ctx ->
            ctx.getBean(AdminApi::class.java).listInteractiveFlowSessions(status = "ongoing").block()
        } ?: fail("the listing should answer")
        return listed.sessions.singleOrNull()?.id
            ?: fail("exactly one session should be ongoing, were: ${listed.sessions.map { it.id }}")
    }

    private fun session(
        sympauthy: SympauthyContainer,
        token: String,
        sessionId: UUID,
    ): AdminInteractiveFlowSessionDetailResource = withApiClient(sympauthy, token = token) { ctx ->
        ctx.getBean(AdminApi::class.java).getInteractiveFlowSession(sessionId).block()
    } ?: fail("the session $sessionId should be readable")

    private fun agent(userAgent: String) = mapOf("User-Agent" to userAgent)

    /**
     * The admin-enabled container for [registry]: password auth, the first-admin bootstrap, and an admin
     * client whose flow token is granted the scope the session listing is gated on.
     */
    private fun adminReadingContainer(
        fixture: DatabaseFixture,
        registry: InteractiveFlowRegistry,
    ): SympauthyContainer {
        registry.withScopes("openid")
        return fixture.applyTo(
            SympauthyContainer(SympauthyImage.resolve())
                .withAdmin()
                .withConfig(
                    mapOf(
                        "auth" to mapOf(
                            "by-password" to mapOf("enabled" to true),
                            "identifier-claims" to listOf("email"),
                        ),
                        "claims" to mapOf("email" to mapOf("enabled" to true)),
                    ),
                )
                .withFlows(registry)
                .withAdminClient(registry, "openid", "admin:interactive-flow-sessions:read"),
        )
    }

    /** Redeems the first-admin bootstrap invitation, signs up, and answers the admin's access token. */
    private fun signUpFirstAdmin(sympauthy: SympauthyContainer, registry: InteractiveFlowRegistry): String {
        val invitationToken = sympauthy.getBootstrapInvitationToken("first-admin")
        val tokens = registry.newFlow()
            .withInvitationToken(invitationToken)
            .withSignUpHandler { mapOf("email" to ADMIN_EMAIL, "password" to PASSWORD) }
            .run()
            .exchange()
        return tokens.accessToken() ?: fail("first-admin sign-up should yield an access token")
    }

    private companion object {
        const val ADMIN_EMAIL = "admin@example.com"

        const val PASSWORD = "Str0ngP@ssw0rd!"

        /** The agent the raw requests of the first scenario claim, so all of them are one place. */
        const val AGENT = "integration-test/1.0"

        /** Comfortably more distinct agents than a session holds entries, so the bound rolls over. */
        const val PLACES = 15
    }
}
