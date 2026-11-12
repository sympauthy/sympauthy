package com.sympauthy.it.feature

import com.sympauthy.api.client.api.AdminApi
import com.sympauthy.api.client.model.AdminInteractiveFlowSessionDebugInformationResource
import com.sympauthy.api.client.model.AdminInteractiveFlowSessionDebugInformationResourceType
import com.sympauthy.api.client.model.AdminInteractiveFlowSessionPurposeProgressResourcePurpose
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.DatabaseFixture
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.FlowOutcome
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.UUID

/**
 * Feature scenario — **what an authorization's purpose says about the session it ran in, as the admin API
 * publishes it.**
 *
 * Each entry names its value by a code and says the type it is rendered as, and the value is published in
 * that type: a string, an array, a date and time, a boolean. The value is held untyped on its way to the
 * wire, so what reaches it is chosen at runtime — which a native image decides without the reflection a JVM
 * has, and a unit test cannot reach.
 *
 * Source: [sympauthy#687](https://github.com/sympauthy/sympauthy/issues/687) — describing an authorization
 * purpose's request with codes the interface labels.
 */
@Tag("feature")
class InteractiveFlowSessionDebugFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "an authorization publishes each value in the type it names on {0}")
    @EnumSource(Database::class)
    fun publishesEachValueInTheTypeItNames(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry -> adminReadingContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val adminToken = signUpFirstAdmin(sympauthy, registry, ADMIN_EMAIL)

            val authorize = httpGet(authorizeUrl(sympauthy, registry))
            assertEquals(303, authorize.statusCode(), "authorize should redirect to the first step")
            val signInPage = authorize.headers().firstValue("Location")
                .orElseGet { error("authorize 303 had no Location") }
            val sessionId = ongoingSessionId(sympauthy, adminToken)

            val result = registry.newFlow()
                .withSignInHandler { Credentials.of(ADMIN_EMAIL, DEFAULT_PASSWORD) }
                .driveFrom(signInPage, registry.redirectUri(), registry.redirectUri())
                .drive()
            assertEquals(FlowOutcome.SUCCESS, result.outcome(), "the flow should complete")

            val debug = oauth2Debug(sympauthy, adminToken, sessionId)
            assertEntry(debug, "client_id", TEXT, registry.clientId())
            assertEntry(debug, "requested_scopes", TEXT_LIST, listOf("openid"))
            assertEntry(debug, "state", PRESENCE, true)
            assertEntry(debug, "code_challenge_method", TEXT, "S256")
            assertNull(debug.entry("invitation_id").value, "an authorization nobody was invited to names none")

            val consentedAt = debug.entry("consented_at")
            assertEquals(DATE, consentedAt.type)
            val written = consentedAt.value as? String
                ?: fail("a date should be written as a string, was: ${consentedAt.value}")
            LocalDateTime.parse(written)
        }
    }

    private fun assertEntry(
        debug: List<AdminInteractiveFlowSessionDebugInformationResource>,
        code: String,
        type: AdminInteractiveFlowSessionDebugInformationResourceType,
        value: Any,
    ) {
        val entry = debug.entry(code)
        assertEquals(type, entry.type, "the type $code is rendered as")
        assertEquals(value, entry.value, "the value of $code")
    }

    private fun List<AdminInteractiveFlowSessionDebugInformationResource>.entry(code: String) =
        singleOrNull { it.code == code } ?: fail("the purpose should publish $code, published: ${map { it.code }}")

    /** The entries of the session's authorization purpose, as the session's own page publishes them. */
    private fun oauth2Debug(
        sympauthy: SympauthyContainer,
        token: String,
        sessionId: UUID,
    ): List<AdminInteractiveFlowSessionDebugInformationResource> {
        val detail = withApiClient(sympauthy, token = token) { ctx ->
            ctx.getBean(AdminApi::class.java).getInteractiveFlowSession(sessionId).block()
        } ?: fail("session $sessionId should be readable")
        return detail.purposes
            .singleOrNull { it.purpose == AdminInteractiveFlowSessionPurposeProgressResourcePurpose.OAUTH2_AUTHORIZE }
            ?.debug
            ?: fail("the session should carry its authorization purpose: ${detail.purposes.map { it.purpose }}")
    }

    /**
     * The identifier of the one session still ongoing, which is the authorization just started: the admin's
     * own sign-up completed before it.
     */
    private fun ongoingSessionId(sympauthy: SympauthyContainer, token: String): UUID {
        val listed = withApiClient(sympauthy, token = token) { ctx ->
            ctx.getBean(AdminApi::class.java)
                .listInteractiveFlowSessions(filter = mutableMapOf("status" to "ongoing"))
                .block()
        } ?: fail("the listing should answer")
        return listed.sessions.singleOrNull()?.id
            ?: fail("exactly one session should be ongoing, were: ${listed.sessions.map { it.id }}")
    }

    /**
     * The admin-enabled container for [registry]: password auth, the first-admin bootstrap, and an admin
     * client whose flow token is granted the scope the session pages are gated on.
     */
    private fun adminReadingContainer(
        fixture: DatabaseFixture,
        registry: InteractiveFlowRegistry,
    ): SympauthyContainer = container(fixture, passwordAuthConfig(), registry)
        .withAdmin()
        .withAdminClient(registry, "openid", "admin:interactive-flow-sessions:read")


    private companion object {
        const val ADMIN_EMAIL = "admin@example.com"

        val TEXT = AdminInteractiveFlowSessionDebugInformationResourceType.TEXT
        val TEXT_LIST = AdminInteractiveFlowSessionDebugInformationResourceType.TEXT_LIST
        val DATE = AdminInteractiveFlowSessionDebugInformationResourceType.DATE
        val PRESENCE = AdminInteractiveFlowSessionDebugInformationResourceType.PRESENCE
    }
}
