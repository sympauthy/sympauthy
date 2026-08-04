package com.sympauthy.it.feature

import com.sympauthy.api.client.api.AdminApi
import com.sympauthy.api.client.model.AdminUserProviderLinkInputResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.DatabaseFixture
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

/**
 * Feature scenarios for **admin-initiated provider linking**
 * (`POST /api/v1/admin/users/{userId}/providers/{providerId}/link`).
 *
 * These cover the endpoint's **start contract** — an administrator (the bootstrap first-admin, whose flow
 * token carries `admin:users:write`) names a user, a client and a configured provider and receives a
 * `redirect_url` to hand to the end-user — and the endpoint's **rejection paths** (missing user, missing
 * client, unknown provider).
 *
 * As with the client twin, the full end-to-end round-trip (confirm → re-authentication → provider
 * authorization → link) is **deferred**: the pinned `testcontainers-sympauthy` mock frontend has no sign-in
 * handler and no mock third-party provider. Reference: issue
 * [#294](https://github.com/sympauthy/sympauthy/issues/294).
 */
@Tag("feature")
class AdminUserProviderLinkFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "admin-initiated provider link returns a redirect_url on {0}")
    @EnumSource(Database::class)
    fun startsLinkAndReturnsRedirectUrl(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry -> adminProviderLinkContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            val link = withApiClient(sympauthy, token = admin.token) { ctx ->
                ctx.getBean(AdminApi::class.java).startLink(
                    admin.userId,
                    PROVIDER_ID,
                    AdminUserProviderLinkInputResource(
                        clientId = registry.clientId(),
                        returnUri = registry.redirectUri(),
                        cancelUri = null,
                    ),
                ).block()
            } ?: fail("admin link init should return a redirect_url")

            assertNotNull(link.redirectUrl, "admin link init should return a redirect_url")
        }
    }

    @ParameterizedTest(name = "admin provider link for a missing user is rejected on {0}")
    @EnumSource(Database::class)
    fun rejectsMissingUser(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry -> adminProviderLinkContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            val rejection = linkRejection(
                sympauthy,
                admin.token,
                userId = UUID.randomUUID(),
                providerId = PROVIDER_ID,
                input = AdminUserProviderLinkInputResource(
                    clientId = registry.clientId(),
                    returnUri = registry.redirectUri(),
                    cancelUri = null,
                ),
            )
            assertEquals(404, rejection.status, "linking an unknown user should be rejected as not found")
        }
    }

    @ParameterizedTest(name = "admin provider link with a missing client is rejected on {0}")
    @EnumSource(Database::class)
    fun rejectsMissingClient(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry -> adminProviderLinkContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            val rejection = linkRejection(
                sympauthy,
                admin.token,
                userId = admin.userId,
                providerId = PROVIDER_ID,
                input = AdminUserProviderLinkInputResource(
                    clientId = "no-such-client",
                    returnUri = registry.redirectUri(),
                    cancelUri = null,
                ),
            )
            assertEquals(404, rejection.status, "naming an unknown client should be rejected as not found")
        }
    }

    @ParameterizedTest(name = "admin provider link with an unknown provider is rejected on {0}")
    @EnumSource(Database::class)
    fun rejectsUnknownProvider(database: Database) {
        withCustomContainer(
            database,
            build = { fixture, registry -> adminProviderLinkContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val admin = signUpFirstAdmin(sympauthy, registry)

            val rejection = linkRejection(
                sympauthy,
                admin.token,
                userId = admin.userId,
                providerId = "not-a-provider",
                input = AdminUserProviderLinkInputResource(
                    clientId = registry.clientId(),
                    returnUri = registry.redirectUri(),
                    cancelUri = null,
                ),
            )
            assertEquals(400, rejection.status, "an unknown provider should be rejected as a bad request")
            assertTrue(
                rejection.body.contains("provider.missing"),
                "should be rejected because the provider is unknown, was: ${rejection.body}",
            )
        }
    }

    // --- Helpers ------------------------------------------------------------------------------------

    /**
     * Builds the admin-enabled container for [registry]: password auth, the first-admin bootstrap, an admin
     * client whose flow token is granted `admin:users:write`, and a configured OAuth2 provider (dummy
     * endpoints — the start endpoint only needs it to resolve as enabled, not to be reachable).
     */
    private fun adminProviderLinkContainer(
        fixture: DatabaseFixture,
        registry: InteractiveFlowRegistry,
    ): SympauthyContainer {
        registry.withScopes("openid")
        return fixture.applyTo(
            SympauthyContainer(SympauthyImage.resolve())
                .withAdmin()
                .withConfig(adminProviderLinkConfig())
                .withFlows(registry)
                .withAdminClient(registry, "openid", "admin:users:write"),
        )
    }

    /** Password auth + a single OAuth2 provider; the admin client itself is layered on by `withAdminClient`. */
    private fun adminProviderLinkConfig(): Map<String, Any> = mapOf(
        "auth" to mapOf(
            "by-password" to mapOf("enabled" to true),
            "identifier-claims" to listOf("email"),
        ),
        "claims" to mapOf("email" to mapOf("enabled" to true)),
        "providers" to mapOf(
            PROVIDER_ID to mapOf(
                "name" to "Test Provider",
                "oauth2" to mapOf(
                    "client-id" to "provider-client-id",
                    "client-secret" to "provider-client-secret",
                    "authorization-url" to "https://provider.example.com/oauth2/authorize",
                    "token-url" to "https://provider.example.com/oauth2/token",
                ),
                "user-info" to mapOf(
                    "url" to "https://provider.example.com/oauth2/userinfo",
                    "paths" to mapOf("sub" to "\$.id", "email" to "\$.email"),
                ),
            ),
        ),
    )

    /** Redeems the first-admin bootstrap invitation, signs up, and returns the admin's token + user id. */
    private fun signUpFirstAdmin(sympauthy: SympauthyContainer, registry: InteractiveFlowRegistry): Admin {
        val invitationToken = sympauthy.getBootstrapInvitationToken("first-admin")
        val tokens = registry.newFlow()
            .withInvitationToken(invitationToken)
            .withSignUpHandler { mapOf("email" to "admin@example.com", "password" to PASSWORD) }
            .run()
            .exchange()
        val token = tokens.accessToken() ?: fail("first-admin sign-up should yield an access token")
        assertTrue(
            tokens.scope()?.split(" ")?.contains("admin:users:write") == true,
            "the first admin should be granted admin:users:write, was: ${tokens.scope()}",
        )
        val idToken = tokens.idToken() ?: fail("the openid scope should yield an id_token")
        val userId = UUID.fromString(verifyIdTokenSignature(sympauthy, idToken).subject)
        return Admin(token, userId)
    }

    private data class Admin(val token: String, val userId: UUID)

    /**
     * Calls `POST /api/v1/admin/users/{userId}/providers/{providerId}/link` expecting a rejection, capturing
     * the HTTP status and error body **inside** the client's context (its response buffer is released once
     * [withApiClient] closes the context).
     */
    private fun linkRejection(
        sympauthy: SympauthyContainer,
        adminToken: String,
        userId: UUID,
        providerId: String,
        input: AdminUserProviderLinkInputResource,
    ): RejectedLink = withApiClient(sympauthy, token = adminToken) { ctx ->
        try {
            ctx.getBean(AdminApi::class.java).startLink(userId, providerId, input).block()
            fail("expected the admin link request to be rejected, but it succeeded")
        } catch (error: HttpClientResponseException) {
            RejectedLink(error.status.code, error.response.getBody(String::class.java).orElse(""))
        }
    }

    private data class RejectedLink(val status: Int, val body: String)

    private companion object {
        const val PASSWORD = "Str0ngP@ssw0rd!"
        const val PROVIDER_ID = "test-provider"
    }
}
