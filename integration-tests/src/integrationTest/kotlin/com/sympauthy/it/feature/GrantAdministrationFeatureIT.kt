package com.sympauthy.it.feature

import com.sympauthy.api.client.api.AdminApi
import com.sympauthy.api.client.model.AdminGrantDetailResource
import com.sympauthy.api.client.model.AdminGrantDetailResourceRevokedBy
import com.sympauthy.api.client.model.AdminGrantDetailResourceStatus
import com.sympauthy.api.client.model.AdminGrantSummaryResource
import com.sympauthy.api.client.model.AdminRevokeGrantsInputResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

/**
 * Feature scenario — **an administrator lists a person's grants, and revokes one of them while the others stay
 * in service.**
 *
 * A person signs up through a client and signs in through it again, as from a laptop and then a phone: two
 * grants of the same client. Searching the grants by the person's address lists both, live and oldest first.
 * The administrator revokes the phone's by its identifier: one grant is revoked, it reads as revoked by an
 * administrator, naming them, the phone's refresh token is refused, and the laptop's still refreshes. Revoking
 * it again revokes none and keeps the revocation the first call wrote, and the live grants of the person are
 * then the laptop's alone.
 *
 * Source: [sympauthy#653](https://github.com/sympauthy/sympauthy/issues/653).
 */
@Tag("feature")
class GrantAdministrationFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "an administrator revokes one grant of a person and leaves the other on {0}")
    @EnumSource(Database::class)
    fun revokesOneGrantAndLeavesTheOther(database: Database) {
        withAdminAndApp(database) { sympauthy, admin, app ->
            val adminToken = signUpFirstAdmin(sympauthy, admin)
            val laptop = signUpAndExchange(sympauthy, app)
            val phone = exchange(
                sympauthy, app,
                app.newFlow().withSignInHandler { Credentials.of(DEFAULT_EMAIL, DEFAULT_PASSWORD) }.run(),
            )

            val listed = listGrants(sympauthy, adminToken, q = DEFAULT_EMAIL)
            assertEquals(2, listed.size, "both sign-ins should be listed as grants of the person: $listed")
            listed.forEach {
                assertEquals(AdminGrantDetailResourceStatus.LIVE, it.status, "grant ${it.grantId} should be live")
                assertEquals(app.clientId(), it.clientId, "grant ${it.grantId} should be the client's")
            }
            val (laptopGrant, phoneGrant) = listed
            val adminGrants = listGrants(sympauthy, adminToken, filter = mapOf("client_id" to admin.clientId()))
            val adminUserId = adminGrants.single().userId

            assertEquals(1, revokeGrant(sympauthy, adminToken, phoneGrant.grantId), "one grant should be revoked")
            val revoked = getGrant(sympauthy, adminToken, phoneGrant.grantId)
            assertEquals(AdminGrantDetailResourceStatus.REVOKED, revoked.status, "the grant should now be revoked")
            assertEquals(AdminGrantDetailResourceRevokedBy.ADMIN, revoked.revokedBy)
            assertEquals(adminUserId, revoked.revokedByUserId, "the grant should name who revoked it")
            assertNotNull(revoked.revokedAt, "the grant should say when it was revoked")

            assertRefreshRefused(
                sympauthy, app, checkNotNull(phone.refreshToken), 400, "invalid_grant",
                "the refresh token of the revoked grant must be refused",
            )
            refresh(sympauthy, app, checkNotNull(laptop.refreshToken))

            assertEquals(0, revokeGrant(sympauthy, adminToken, phoneGrant.grantId), "no grant should be left to revoke")
            val again = getGrant(sympauthy, adminToken, phoneGrant.grantId)
            assertEquals(revoked.revokedAt, again.revokedAt, "a second revocation should keep the first one's date")

            val live = listGrants(sympauthy, adminToken, q = DEFAULT_EMAIL, filter = mapOf("status" to "live"))
            assertEquals(listOf(laptopGrant.grantId), live.map { it.grantId }, "only the laptop should stay live")
        }
    }

    /**
     * Starts a container holding the admin client of [admin] and the confidential client of [app], whose grants
     * refresh, and runs [block] against it.
     */
    private fun withAdminAndApp(
        database: Database,
        block: (SympauthyContainer, InteractiveFlowRegistry, InteractiveFlowRegistry) -> Unit,
    ) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.publicClient(ADMIN_CLIENT_ID)).use { admin ->
                InteractiveFlowRegistry.forClient(Client.confidentialClient(APP_CLIENT_ID, APP_CLIENT_SECRET))
                    .withFlowId(APP_FLOW_ID)
                    .use { app ->
                        val refreshEnabled = mapOf("auth" to mapOf("token" to mapOf("refresh-enabled" to true)))
                        container(fixture, config(app) and refreshEnabled, admin, app)
                            .withAdmin()
                            .withAdminClient(admin, "openid", "admin:grants:read", "admin:grants:write")
                            .use { sympauthy -> withStartedContainer(sympauthy) { block(it, admin, app) } }
                    }
            }
        }
    }

    /** Redeems the first-admin bootstrap invitation, signs up, and answers the admin's access token. */
    private fun signUpFirstAdmin(sympauthy: SympauthyContainer, registry: InteractiveFlowRegistry): String {
        val invitationToken = sympauthy.getBootstrapInvitationToken("first-admin")
        val signedUp = registry.newFlow()
            .withInvitationToken(invitationToken)
            .withSignUpHandler { credentials(ADMIN_EMAIL) }
            .run()
        return exchange(sympauthy, registry, signedUp).accessToken
    }

    private fun listGrants(
        sympauthy: SympauthyContainer,
        token: String,
        q: String? = null,
        filter: Map<String, String> = emptyMap(),
    ): List<AdminGrantSummaryResource> = withApiClient(sympauthy, token = token) { ctx ->
        ctx.getBean(AdminApi::class.java).listGrants(filter = filter.toMutableMap(), q = q).block()
    }?.grants ?: fail("the grants should be listed")

    private fun getGrant(sympauthy: SympauthyContainer, token: String, grantId: UUID): AdminGrantDetailResource =
        withApiClient(sympauthy, token = token) { ctx ->
            ctx.getBean(AdminApi::class.java).getGrant(grantId).block()
        } ?: fail("grant $grantId should be read")

    /** Revokes the grant [grantId] by its identifier, and answers how many grants were revoked. */
    private fun revokeGrant(sympauthy: SympauthyContainer, token: String, grantId: UUID): Int =
        withApiClient(sympauthy, token = token) { ctx ->
            ctx.getBean(AdminApi::class.java)
                .revokeGrants(AdminRevokeGrantsInputResource(grantIds = listOf(grantId)))
                .block()
        }?.grantsRevoked ?: fail("revoking grant $grantId should answer how many grants were revoked")

    private companion object {
        const val ADMIN_CLIENT_ID = "admin-console"
        const val ADMIN_EMAIL = "admin@example.com"
        const val APP_CLIENT_ID = "app"
        const val APP_CLIENT_SECRET = "grant-administration-secret-value"
        const val APP_FLOW_ID = "app-flow"
    }
}
