package com.sympauthy.it.feature

import com.sympauthy.api.client.api.AdminApi
import com.sympauthy.api.client.model.AdminInteractiveFlowPurposeResourceValue
import com.sympauthy.api.client.model.AdminInteractiveFlowSessionPurposeProgressResourceStatus
import com.sympauthy.api.client.model.AdminInteractiveFlowSessionSummaryResourceStatus
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.SympauthyContainer
import io.micronaut.http.client.exceptions.HttpClientResponseException
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
 * Feature scenarios for **reading the live interactive flow sessions**
 * (`GET /api/v1/admin/interactive-flow-sessions` and `/{sessionId}`).
 *
 * An administrator signs up through the first-admin bootstrap invitation, which leaves one completed session
 * behind. The listing is then read back: the session is there, it carries the client that started it and the
 * purpose that initiated it, its status is `completed` rather than the `failed` the flow's own projection
 * would report near expiry, and the detail names every purpose it carried with what each one has to say. The
 * rejections cover a filter value this deployment does not have and a token without the scope.
 *
 * Reference: issue [#409](https://github.com/sympauthy/sympauthy/issues/409).
 */
@Tag("feature")
class AdminInteractiveFlowSessionFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "the listing shows the session the sign-up left behind on {0}")
    @EnumSource(Database::class)
    fun listsTheSessionASignUpLeftBehind(database: Database) {
        withCustomContainer(database, build = ::adminSessionContainer) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            val sessions = withApiClient(sympauthy, token = admin.token) { ctx ->
                ctx.getBean(AdminApi::class.java).listInteractiveFlowSessions(size = 100).block()
            } ?: fail("the interactive flow session listing should answer with a body")

            val session = sessions.sessions.single()
            assertEquals(
                AdminInteractiveFlowSessionSummaryResourceStatus.COMPLETED,
                session.status,
                "the sign-up completed, and completion outranks expiry",
            )
            assertEquals(
                AdminInteractiveFlowPurposeResourceValue.OAUTH2_AUTHORIZE,
                session.initiatingPurpose.value,
            )
            assertTrue(
                session.initiatingPurpose.displayName.isNotBlank(),
                "a purpose carries the label a person reads it under wherever it appears",
            )
            assertEquals(registry.clientId(), session.clientId, "the session names the client that started it")
            assertTrue(session.signedUp, "the first admin was created by this session")
            assertNull(session.currentPurpose, "a completed session has no purpose left to drive")
            assertNotNull(session.user, "the account is promoted by the completion, so the listing names it")
            assertEquals(admin.userId, session.user?.userId)
        }
    }

    @ParameterizedTest(name = "the detail names every purpose and what it has to say on {0}")
    @EnumSource(Database::class)
    fun readsTheDetailOfOneSession(database: Database) {
        withCustomContainer(database, build = ::adminSessionContainer) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            val detail = withApiClient(sympauthy, token = admin.token) { ctx ->
                val api = ctx.getBean(AdminApi::class.java)
                val id = api.listInteractiveFlowSessions(size = 100).block()!!.sessions.single().id
                api.getInteractiveFlowSession(id).block()
            } ?: fail("the interactive flow session detail should answer with a body")

            val authorize = detail.purposes
                .single { it.purpose.value == AdminInteractiveFlowPurposeResourceValue.OAUTH2_AUTHORIZE }
            assertEquals(AdminInteractiveFlowSessionPurposeProgressResourceStatus.COMPLETED, authorize.status)
            assertEquals(
                detail.initiatingPurpose,
                authorize.purpose,
                "one purpose is published as one shape, whether it names the session or an entry of its list",
            )

            val debug = authorize.debug.associate { it.displayName to it.value }
            assertEquals(registry.clientId(), debug["Client"])
            assertNotNull(debug["Requested scopes"], "the authorize purpose names what the client asked for")
            assertTrue(
                debug["State"] in setOf("present", "absent"),
                "the state is reported as present or absent, never published: was ${debug["State"]}",
            )
            assertFalse(
                authorize.debug.mapNotNull { it.value }.any { it.length > 200 },
                "no debug value should be a raw secret-sized blob",
            )
            assertNull(detail.errorDetailsId, "a completed session failed with nothing")
        }
    }

    @ParameterizedTest(name = "a filter naming nothing this deployment has is refused on {0}")
    @EnumSource(Database::class)
    fun rejectsAFilterValueTheDeploymentDoesNotHave(database: Database) {
        withCustomContainer(database, build = ::adminSessionContainer) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            withApiClient(sympauthy, token = admin.token) { ctx ->
                val api = ctx.getBean(AdminApi::class.java)
                listOf<Pair<String, () -> Unit>>(
                    "status" to { api.listInteractiveFlowSessions(status = "nope").block() },
                    "client" to { api.listInteractiveFlowSessions(client = "nope").block() },
                    "purpose" to { api.listInteractiveFlowSessions(purpose = "nope").block() },
                    "order" to { api.listInteractiveFlowSessions(order = "nope").block() },
                ).forEach { (parameter, call) ->
                    try {
                        call()
                        fail("$parameter=nope should have been refused")
                    } catch (error: HttpClientResponseException) {
                        assertEquals(400, error.status.code, "$parameter=nope should be refused as a bad request")
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "a token without the scope is refused on {0}")
    @EnumSource(Database::class)
    fun rejectsATokenWithoutTheScope(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry ->
                fixture.applyTo(
                    SympauthyContainer(SympauthyImage.resolve())
                        .withAdmin()
                        .withConfig(passwordConfig())
                        .withFlows(registry)
                        // Granted the user scope and not this one, so the surface gate is what refuses.
                        .withAdminClient(registry, "openid", "admin:users:read"),
                )
            },
        ) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            withApiClient(sympauthy, token = admin.token) { ctx ->
                try {
                    ctx.getBean(AdminApi::class.java).listInteractiveFlowSessions().block()
                    fail("a token without admin:interactive-flow-sessions:read should have been refused")
                } catch (error: HttpClientResponseException) {
                    assertEquals(403, error.status.code)
                }
            }
        }
    }

    @ParameterizedTest(name = "an unknown session identifier answers not found on {0}")
    @EnumSource(Database::class)
    fun rejectsAnUnknownSessionIdentifier(database: Database) {
        withCustomContainer(database, build = ::adminSessionContainer) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            withApiClient(sympauthy, token = admin.token) { ctx ->
                try {
                    ctx.getBean(AdminApi::class.java).getInteractiveFlowSession(UUID.randomUUID()).block()
                    fail("an unknown session identifier should have been refused")
                } catch (error: HttpClientResponseException) {
                    assertEquals(404, error.status.code)
                }
            }
        }
    }

    private fun adminSessionContainer(
        fixture: com.sympauthy.it.DatabaseFixture,
        registry: com.sympauthy.testcontainers.flow.InteractiveFlowRegistry,
    ): SympauthyContainer = fixture.applyTo(
        SympauthyContainer(SympauthyImage.resolve())
            .withAdmin()
            .withConfig(passwordConfig())
            .withFlows(registry)
            .withAdminClient(registry, "openid", "admin:interactive-flow-sessions:read"),
    )

    private fun passwordConfig(): Map<String, Any> = mapOf(
        "auth" to mapOf(
            "by-password" to mapOf("enabled" to true),
            "identifier-claims" to listOf("email"),
        ),
        "claims" to mapOf("email" to mapOf("enabled" to true)),
    )

    /** Redeems the first-admin bootstrap invitation, signs up, and returns the admin's token + user id. */
    private fun signUpFirstAdmin(
        sympauthy: SympauthyContainer,
        registry: com.sympauthy.testcontainers.flow.InteractiveFlowRegistry,
    ): Admin {
        val invitationToken = sympauthy.getBootstrapInvitationToken("first-admin")
        val tokens = registry.newFlow()
            .withInvitationToken(invitationToken)
            .withSignUpHandler { mapOf("email" to "admin@example.com", "password" to PASSWORD) }
            .run()
            .exchange()
        val token = tokens.accessToken() ?: fail("first-admin sign-up should yield an access token")
        val idToken = tokens.idToken() ?: fail("the openid scope should yield an id_token")
        val userId = UUID.fromString(verifyIdTokenSignature(sympauthy, idToken).subject)
        return Admin(token, userId)
    }

    private data class Admin(val token: String, val userId: UUID)

    private companion object {
        const val PASSWORD = "Str0ngP@ssw0rd!"
    }
}
