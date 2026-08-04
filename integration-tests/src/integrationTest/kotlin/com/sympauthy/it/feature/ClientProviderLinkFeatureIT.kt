package com.sympauthy.it.feature

import com.sympauthy.api.client.api.ClientApi
import com.sympauthy.api.client.model.ClientProviderLinkInputResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.DatabaseFixture
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.Client
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

/**
 * Feature scenarios for **client-initiated provider linking** (`POST /api/v1/client/providers/{providerId}/link`).
 *
 * These cover the endpoint's **start contract** — a confidential client authenticates with a
 * `client_credentials` token holding `users:providers:write`, names one of its signed-in end-users (via that
 * user's access token) and a configured, enabled provider, and receives a signed `state` + a `redirect_url`
 * to drive the end-user's browser to — and the endpoint's **rejection paths** (unregistered `return_uri`,
 * caller missing the scope, unknown provider, an end-user token issued to a different client).
 *
 * The full end-to-end round-trip (confirm → re-authentication → provider authorization → link → `return_uri`)
 * is **deferred**: the pinned `testcontainers-sympauthy` mock frontend has no sign-in handler and no mock
 * third-party provider, so it cannot drive the re-authentication and provider-authorization steps. Driving
 * the link to completion needs a `testcontainers-sympauthy` enhancement (a follow-up).
 *
 * Reference: issue [#294](https://github.com/sympauthy/sympauthy/issues/294).
 */
@Tag("feature")
class ClientProviderLinkFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "client-initiated provider link returns state and redirect_url on {0}")
    @EnumSource(Database::class)
    fun startsLinkAndReturnsRedirectUrl(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(clientId, CLIENT_SECRET),
            build = { fixture, registry -> providerLinkContainer(fixture, registry) },
        ) { sympauthy, registry ->
            // The calling client authenticates with a client-credentials token holding users:providers:write.
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:providers:write")
            // An end-user of that client, and the access token identifying them to the link endpoint.
            val userToken = signUpAccessToken(registry, "ada@example.com")

            val link = withApiClient(sympauthy, token = callerToken) { ctx ->
                ctx.getBean(ClientApi::class.java).startLink1(
                    PROVIDER_ID,
                    ClientProviderLinkInputResource(
                        accessToken = userToken,
                        returnUri = providerLinkReturnUri(registry),
                        cancelUri = providerLinkCancelUri(registry),
                    ),
                ).block()
            } ?: fail("link init should return a state and redirect_url")

            assertNotNull(link.state, "link init should return a signed state")
            assertNotNull(link.redirectUrl, "link init should return a redirect_url to drive the end-user to")
        }
    }

    @ParameterizedTest(name = "provider link with an unregistered return_uri is rejected on {0}")
    @EnumSource(Database::class)
    fun rejectsUnregisteredReturnUri(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(clientId, CLIENT_SECRET),
            build = { fixture, registry -> providerLinkContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:providers:write")
            val userToken = signUpAccessToken(registry, "no-return@example.com")

            val rejection = linkRejection(
                sympauthy,
                callerToken,
                ClientProviderLinkInputResource(
                    accessToken = userToken,
                    returnUri = "https://unregistered.example.com/return",
                    cancelUri = null,
                ),
            )
            assertEquals(400, rejection.status, "an unregistered return_uri should be rejected as a bad request")
            assertTrue(
                rejection.body.contains("client.redirect_uri.not_allowed"),
                "should be rejected as a disallowed redirect URI, was: ${rejection.body}",
            )
        }
    }

    @ParameterizedTest(name = "provider link by a caller lacking users:providers:write is forbidden on {0}")
    @EnumSource(Database::class)
    fun rejectsCallerWithoutProvidersWriteScope(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(clientId, CLIENT_SECRET),
            build = { fixture, registry -> providerLinkContainer(fixture, registry) },
        ) { sympauthy, registry ->
            // A client-credentials token WITHOUT users:providers:write — authenticates the client but lacks the scope.
            val callerToken = clientCredentialsToken(sympauthy, registry)
            val userToken = signUpAccessToken(registry, "no-scope@example.com")

            val rejection = linkRejection(
                sympauthy,
                callerToken,
                ClientProviderLinkInputResource(
                    accessToken = userToken,
                    returnUri = providerLinkReturnUri(registry),
                    cancelUri = null,
                ),
            )
            assertEquals(403, rejection.status, "a caller without users:providers:write should be forbidden")
        }
    }

    @ParameterizedTest(name = "provider link with an unknown provider is rejected on {0}")
    @EnumSource(Database::class)
    fun rejectsUnknownProvider(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(clientId, CLIENT_SECRET),
            build = { fixture, registry -> providerLinkContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:providers:write")
            val userToken = signUpAccessToken(registry, "unknown-provider@example.com")

            val rejection = linkRejection(
                sympauthy,
                callerToken,
                ClientProviderLinkInputResource(
                    accessToken = userToken,
                    returnUri = providerLinkReturnUri(registry),
                    cancelUri = null,
                ),
                providerId = "not-a-provider",
            )
            assertEquals(
                404,
                rejection.status,
                "an unknown provider should be rejected as not found (coherent with unknown user/client), was: ${rejection.body}",
            )
        }
    }

    @ParameterizedTest(name = "provider link with an end-user token issued to another client is rejected on {0}")
    @EnumSource(Database::class)
    fun rejectsUserTokenIssuedToAnotherClient(database: Database) {
        // Two clients, each with its own mock frontend: the caller (holding users:providers:write) and another
        // client whose end-user token we present to the caller's link endpoint.
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.confidentialClient(clientId, CLIENT_SECRET))
                .withScopes("openid").use { caller ->
                    InteractiveFlowRegistry.forClient(Client.publicClient(OTHER_CLIENT_ID))
                        .withFlowId("other").withScopes("openid").use { other ->
                            fixture.applyTo(
                                SympauthyContainer(SympauthyImage.resolve())
                                    .withConfig(twoClientConfig(caller, other))
                                    .withFlows(caller)
                                    .withFlows(other),
                            ).use { sympauthy ->
                                withStartedContainer(sympauthy) {
                                    val callerToken = clientCredentialsToken(sympauthy, caller, "users:providers:write")
                                    val foreignUserToken = signUpAccessToken(other, "eve@example.com")

                                    val rejection = linkRejection(
                                        sympauthy,
                                        callerToken,
                                        ClientProviderLinkInputResource(
                                            accessToken = foreignUserToken,
                                            returnUri = providerLinkReturnUri(caller),
                                            cancelUri = null,
                                        ),
                                    )
                                    assertEquals(400, rejection.status, "a token issued to another client should be rejected")
                                    assertTrue(
                                        rejection.body.contains("invalid_access_token"),
                                        "should be rejected as an invalid access token, was: ${rejection.body}",
                                    )
                                }
                            }
                        }
                }
        }
    }

    // --- Helpers ------------------------------------------------------------------------------------

    /** The `return_uri` a provider-link flow redirects the end-user to on success. */
    private fun providerLinkReturnUri(registry: InteractiveFlowRegistry): String =
        "${registry.frontendUrl()}/link-return"

    /** The `cancel_uri` a provider-link flow redirects the end-user to on cancellation. */
    private fun providerLinkCancelUri(registry: InteractiveFlowRegistry): String =
        "${registry.frontendUrl()}/link-cancel"

    /**
     * The full server configuration for the client provider-link API: password auth, a confidential client
     * that owns [registry]'s flow and is allowed both the `authorization_code` grant (to obtain an end-user
     * access token) and the `client_credentials` grant carrying `users:providers:write` (to call the link
     * endpoint), with the return/cancel URIs registered as redirect URIs, plus a configured OAuth2 provider
     * (dummy endpoints — the start endpoint only needs it to resolve as enabled, not to be reachable).
     */
    private fun providerLinkConfig(registry: InteractiveFlowRegistry): Map<String, Any> {
        val secret = registry.clientSecret()
            ?: error("client-initiated provider link requires a confidential client (client_credentials grant)")
        return linkedMapOf(
            "auth" to mapOf(
                "by-password" to mapOf("enabled" to true),
                "identifier-claims" to listOf("email"),
            ),
            "claims" to mapOf("email" to mapOf("enabled" to true)),
            "features" to mapOf("grant-unhandled-scopes" to true),
            "templates" to mapOf(
                "clients" to mapOf("default" to mapOf("authorization-flow" to registry.flowId())),
            ),
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
            "clients" to mapOf(
                registry.clientId() to mapOf(
                    "secret" to secret,
                    "authorizationFlow" to registry.flowId(),
                    "allowed-grant-types" to listOf("authorization_code", "client_credentials"),
                    "allowed-scopes" to listOf("openid", "users:providers:write"),
                    "default-scopes" to listOf("openid"),
                    "allowed-redirect-uris" to listOf(
                        registry.redirectUri(),
                        providerLinkReturnUri(registry),
                        providerLinkCancelUri(registry),
                    ),
                ),
            ),
        )
    }

    /** Builds the standard single-client provider-link container for [registry] (a confidential client). */
    private fun providerLinkContainer(
        fixture: DatabaseFixture,
        registry: InteractiveFlowRegistry,
    ): SympauthyContainer {
        registry.withScopes("openid")
        return fixture.applyTo(
            SympauthyContainer(SympauthyImage.resolve())
                .withConfig(providerLinkConfig(registry))
                .withFlows(registry),
        )
    }

    /** [providerLinkConfig] for [caller] plus a second, public [other] client with its own flow. */
    private fun twoClientConfig(
        caller: InteractiveFlowRegistry,
        other: InteractiveFlowRegistry,
    ): Map<String, Any> {
        val base = providerLinkConfig(caller)
        @Suppress("UNCHECKED_CAST")
        val clients = (base["clients"] as Map<String, Any>) + (
            other.clientId() to mapOf(
                "public" to true,
                "authorizationFlow" to other.flowId(),
                "allowed-grant-types" to listOf("authorization_code"),
                "allowed-scopes" to listOf("openid"),
                "allowed-redirect-uris" to listOf(other.redirectUri()),
            )
            )
        return base + mapOf("clients" to clients)
    }

    /** Signs up a fresh end-user through [registry]'s flow and returns their access token. */
    private fun signUpAccessToken(registry: InteractiveFlowRegistry, email: String): String {
        val token = registry.newFlow()
            .withSignUpHandler { mapOf("email" to email, "password" to PASSWORD) }
            .run()
            .exchange()
            .accessToken()
        assertNotNull(token, "sign-up should yield an end-user access token")
        return token
    }

    /**
     * Calls `POST /api/v1/client/providers/{providerId}/link` expecting a rejection, and captures the HTTP
     * status and error body **inside** the client's context (its response buffer is released once
     * [withApiClient] closes the context, so the body must be read before returning).
     */
    private fun linkRejection(
        sympauthy: SympauthyContainer,
        callerToken: String,
        input: ClientProviderLinkInputResource,
        providerId: String = PROVIDER_ID,
    ): RejectedLink = withApiClient(sympauthy, token = callerToken) { ctx ->
        try {
            ctx.getBean(ClientApi::class.java).startLink1(providerId, input).block()
            fail("expected the link request to be rejected, but it succeeded")
        } catch (error: HttpClientResponseException) {
            RejectedLink(error.status.code, error.response.getBody(String::class.java).orElse(""))
        }
    }

    /** The HTTP status and raw error body (carrying the server's `error_code`) of a rejected link call. */
    private data class RejectedLink(val status: Int, val body: String)

    private companion object {
        const val CLIENT_SECRET = "s3cr3t-link"
        const val OTHER_CLIENT_ID = "other-app"
        const val PASSWORD = "Str0ngP@ssw0rd!"
        const val PROVIDER_ID = "test-provider"
    }
}
