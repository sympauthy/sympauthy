package com.sympauthy.it.feature

import com.sympauthy.api.client.api.AdminApi
import com.sympauthy.api.client.model.AdminRevokeGrantsInputResource
import com.sympauthy.api.client.model.AdminUserDetailResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.ZoneOffset
import java.util.UUID

/**
 * Feature scenario — **an administrator reads when a person last authenticated and last had a grant
 * refreshed, finds dormant accounts by either, and both dates outlive what they came from.**
 *
 * A person signs up through a client: their last authentication is the `auth_time` of the id token, and they
 * have no refresh on record. The client refreshes, which moves the last refresh and leaves the last
 * authentication and the `auth_time` of the new tokens where they were. Signing in again moves the last
 * authentication. Revoking the person's grants refuses the next refresh, and neither of those moves a date. A
 * second person and the administrator make the collection's filters and order mean something, and the user a
 * flow session embeds states neither date.
 *
 * Source: [sympauthy#677](https://github.com/sympauthy/sympauthy/issues/677).
 */
@Tag("feature")
class UserLastActivityFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "the last dates of a person follow what they do and outlive their grants on {0}")
    @EnumSource(Database::class)
    fun lastDatesFollowThePersonAndOutliveTheirGrants(database: Database) {
        withAdminAndApp(database) { sympauthy, admin, app ->
            val adminToken = signUpFirstAdmin(sympauthy, admin, ADMIN_EMAIL)
            val tokens = signUpAndExchange(sympauthy, app)
            val userId = userIdOf(sympauthy, adminToken, DEFAULT_EMAIL)
            val authTime = checkNotNull(
                verifyIdTokenSignature(sympauthy, requireIdToken(tokens.idToken)).getLongClaim(AUTH_TIME)
            )

            val signedUp = getUser(sympauthy, adminToken, userId)
            assertEquals(
                authTime,
                checkNotNull(signedUp.lastAuthenticatedAt).toEpochSecond(ZoneOffset.UTC),
                "the last authentication should be the moment the tokens state",
            )
            assertNull(signedUp.lastRefreshedAt, "nothing has been refreshed yet")

            val refreshed = refresh(sympauthy, app, checkNotNull(tokens.refreshToken))
            val afterRefresh = getUser(sympauthy, adminToken, userId)
            assertNotNull(afterRefresh.lastRefreshedAt, "a refresh should move the last refresh")
            assertEquals(
                signedUp.lastAuthenticatedAt,
                afterRefresh.lastAuthenticatedAt,
                "a refresh is a client acting alone, so the last authentication should stay",
            )
            assertEquals(
                authTime,
                verifyIdTokenSignature(sympauthy, requireIdToken(refreshed.idToken)).getLongClaim(AUTH_TIME),
                "the refreshed tokens should still state the original authentication",
            )

            exchange(
                sympauthy, app,
                app.newFlow().withSignInHandler { Credentials.of(DEFAULT_EMAIL, DEFAULT_PASSWORD) }.run(),
            )
            val signedInAgain = getUser(sympauthy, adminToken, userId)
            assertTrue(
                checkNotNull(signedInAgain.lastAuthenticatedAt).isAfter(signedUp.lastAuthenticatedAt),
                "a second sign-in should move the last authentication",
            )
            assertEquals(
                afterRefresh.lastRefreshedAt,
                signedInAgain.lastRefreshedAt,
                "a sign-in should leave the last refresh",
            )

            assertTrue(revokeGrantsOf(sympauthy, adminToken, userId) > 0, "the person should hold grants to revoke")
            assertRefreshRefused(
                sympauthy, app, checkNotNull(tokens.refreshToken), 400, "invalid_grant",
                "the refresh token of a revoked grant must be refused",
            )
            val revoked = getUser(sympauthy, adminToken, userId)
            assertEquals(signedInAgain.lastAuthenticatedAt, revoked.lastAuthenticatedAt)
            assertEquals(
                signedInAgain.lastRefreshedAt,
                revoked.lastRefreshedAt,
                "neither a revocation nor a refused refresh should move a date",
            )
        }
    }

    @ParameterizedTest(name = "the user collection filters and orders on both dates on {0}")
    @EnumSource(Database::class)
    fun collectionFiltersAndOrdersOnBothDates(database: Database) {
        withAdminAndApp(database) { sympauthy, admin, app ->
            val adminToken = signUpFirstAdmin(sympauthy, admin, ADMIN_EMAIL)
            val adminId = userIdOf(sympauthy, adminToken, ADMIN_EMAIL)
            val ada = signUpAndExchange(sympauthy, app)
            val adaId = userIdOf(sympauthy, adminToken, DEFAULT_EMAIL)
            val grace = exchange(
                sympauthy, app,
                app.newFlow().withSignUpHandler { credentials(OTHER_EMAIL) }.run(),
            )
            val graceId = userIdOf(sympauthy, adminToken, OTHER_EMAIL)
            refresh(sympauthy, app, checkNotNull(ada.refreshToken))
            refresh(sympauthy, app, checkNotNull(grace.refreshToken))

            assertEquals(
                listOf(adminId),
                listUserIds(sympauthy, adminToken, mapOf("last_refreshed_at.is_null" to "true")),
                "only the administrator should have no refresh on record",
            )
            assertEquals(
                listOf(graceId, adaId),
                listUserIds(
                    sympauthy, adminToken,
                    mapOf("last_refreshed_at.is_null" to "false"),
                    sort = "-last_refreshed_at",
                ),
                "the most recently refreshed should come first",
            )
            val graceSignedUp = checkNotNull(getUser(sympauthy, adminToken, graceId).lastAuthenticatedAt)
            assertEquals(
                setOf(adminId, adaId),
                listUserIds(
                    sympauthy, adminToken,
                    mapOf("last_authenticated_at.lt" to graceSignedUp.toString()),
                ).toSet(),
                "the accounts unused since a date should be the ones found",
            )

            val capabilities = withApiClient(sympauthy, token = adminToken) { ctx ->
                ctx.getBean(AdminApi::class.java).getUserCapabilities().block()
            } ?: fail("the capabilities should be readable")
            listOf("last_authenticated_at", "last_refreshed_at").forEach { field ->
                assertTrue(capabilities.filters.any { it.field == field }, "$field should be filterable")
                assertTrue(capabilities.sorts.any { it.field == field }, "$field should be sortable")
            }

            val embedded = withApiClient(sympauthy, token = adminToken) { ctx ->
                ctx.getBean(AdminApi::class.java).listInteractiveFlowSessions(filter = mutableMapOf()).block()
            }?.sessions?.mapNotNull { it.user }?.single { it.userId == graceId }
                ?: fail("a session should name the person")
            assertNull(embedded.lastAuthenticatedAt, "the user a session embeds should not say when they signed in")
            assertNull(embedded.lastRefreshedAt, "the user a session embeds should not say when they refreshed")
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
                            .withAdminClient(
                                admin,
                                "openid",
                                "admin:users:read",
                                "admin:grants:write",
                                "admin:interactive-flow-sessions:read",
                            )
                            .use { sympauthy -> withStartedContainer(sympauthy) { block(it, admin, app) } }
                    }
            }
        }
    }


    private fun userIdOf(sympauthy: SympauthyContainer, token: String, email: String): UUID =
        withApiClient(sympauthy, token = token) { ctx ->
            ctx.getBean(AdminApi::class.java).listUsers(filter = mutableMapOf(), q = email).block()
        }?.users?.single()?.userId ?: fail("$email should be found")

    private fun listUserIds(
        sympauthy: SympauthyContainer,
        token: String,
        filter: Map<String, String>,
        sort: String? = null,
    ): List<UUID> = withApiClient(sympauthy, token = token) { ctx ->
        ctx.getBean(AdminApi::class.java).listUsers(filter = filter.toMutableMap(), sort = sort).block()
    }?.users?.map { it.userId } ?: fail("the users should be listed")

    private fun getUser(sympauthy: SympauthyContainer, token: String, userId: UUID): AdminUserDetailResource =
        withApiClient(sympauthy, token = token) { ctx ->
            ctx.getBean(AdminApi::class.java).getUser(userId).block()
        } ?: fail("user $userId should be read")

    /** Revokes every grant of [userId], and answers how many were revoked. */
    private fun revokeGrantsOf(sympauthy: SympauthyContainer, token: String, userId: UUID): Int =
        withApiClient(sympauthy, token = token) { ctx ->
            ctx.getBean(AdminApi::class.java)
                .revokeGrants(AdminRevokeGrantsInputResource(userIds = listOf(userId)))
                .block()
        }?.grantsRevoked ?: fail("revoking should answer how many grants were revoked")

    private companion object {
        const val ADMIN_CLIENT_ID = "admin-console"
        const val ADMIN_EMAIL = "admin@example.com"
        const val OTHER_EMAIL = "grace@example.com"
        const val APP_CLIENT_ID = "app"
        const val APP_CLIENT_SECRET = "user-last-activity-secret-value"
        const val APP_FLOW_ID = "app-flow"
        const val AUTH_TIME = "auth_time"
    }
}
