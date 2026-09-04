package com.sympauthy.it.feature

import com.sympauthy.api.client.api.ClientApi
import com.sympauthy.api.client.model.ClientMfaEnrollmentInputResource
import com.sympauthy.api.client.model.ClientMfaEnrollmentResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.DatabaseFixture
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.ConfirmDecision
import com.sympauthy.testcontainers.flow.FlowOutcome
import com.sympauthy.testcontainers.flow.FlowStep
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import com.sympauthy.testcontainers.flow.Totp
import io.micronaut.http.client.exceptions.HttpClientResponseException
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenarios for **on-demand MFA enrollment started by a client** (`POST /api/v1/client/mfa/enrollment`).
 *
 * The happy path proves the whole standalone (non-authorize) enrollment works end-to-end: a confidential client
 * authenticates with a `client_credentials` token holding `users:mfa:write`, starts an enrollment for one of its
 * signed-in end-users, and the returned `redirect_url` is driven through the state-secured flow API — the `CONFIRM`
 * step then TOTP enrollment — terminating by redirecting the end-user back to the client-provided `return_uri`.
 *
 * The remaining scenarios are this same feature's rejection paths — an unregistered `return_uri`, a caller token
 * missing the required scope, MFA disabled on the server, and an end-user token issued to a different client — each
 * proving the endpoint refuses the request. All run against each supported database.
 *
 * Reference: issue [#280](https://github.com/sympauthy/sympauthy/issues/280) (the endpoint) and
 * [#285](https://github.com/sympauthy/sympauthy/issues/285) (these tests).
 */
@Tag("feature")
class ClientMfaEnrollmentFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "client-initiated MFA enrollment enrolls TOTP and returns to return_uri on {0}")
    @EnumSource(Database::class)
    fun startsEnrollmentAndDrivesItToReturnUri(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(clientId, CLIENT_SECRET),
            build = { fixture, registry -> mfaEnrollmentContainer(fixture, registry) },
        ) { sympauthy, registry ->
            // The calling client authenticates with a client-credentials token holding users:mfa:write.
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:mfa:write")
            // An end-user of that client, and the access token identifying them to the enrollment endpoint.
            val userToken = signUpAccessToken(registry, "ada@example.com")

            // Start the standalone enrollment on behalf of that user, authenticated as the calling client.
            val returnUri = mfaEnrollmentReturnUri(registry)
            val cancelUri = mfaEnrollmentCancelUri(registry)
            val enrollment = withApiClient(sympauthy, token = callerToken) { ctx ->
                ctx.getBean(ClientApi::class.java).startEnrollment1(
                    ClientMfaEnrollmentInputResource(accessToken = userToken, returnUri = returnUri, cancelUri = cancelUri),
                ).block()
            } ?: fail("enrollment init should return a state and redirect_url")

            // Drive the returned link through confirm → TOTP enrollment.
            val flow = registry.newFlow()
                .withConfirmHandler { resource ->
                    assertEquals("ENROLL_MFA", resource.action(), "confirm should describe the action")
                    assertEquals(clientId, resource.initiatingClientId(), "confirm should name the initiating client")
                    ConfirmDecision.CONFIRM
                }
                .withTotpEnrollmentHandler { data -> Totp.code(data.secret()) }
            val result = flow.driveFrom(enrollment.redirectUrl, returnUri, cancelUri).drive()

            assertEquals(FlowOutcome.SUCCESS, result.outcome(), "approving confirm + enrolling TOTP should complete")
            assertTrue(
                result.stepTypes().contains(FlowStep.Type.CONFIRM),
                "flow should include a confirm step, was: ${result.stepTypes()}",
            )
            assertTrue(
                result.stepTypes().contains(FlowStep.Type.MFA),
                "flow should include an MFA enrollment step, was: ${result.stepTypes()}",
            )
            // The PLAIN enrollment redirects the end-user back to return_uri verbatim; the driver records the
            // terminal request's path, so compare against return_uri's path (not the mock frontend's origin).
            val returnPath = URI.create(returnUri).path
            assertTrue(
                result.terminalUrl().startsWith(returnPath),
                "enrollment should redirect back to return_uri ($returnPath), was: ${result.terminalUrl()}",
            )
        }
    }

    @ParameterizedTest(name = "enrollment with an unregistered return_uri is rejected on {0}")
    @EnumSource(Database::class)
    fun rejectsUnregisteredReturnUri(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(clientId, CLIENT_SECRET),
            build = { fixture, registry -> mfaEnrollmentContainer(fixture, registry) },
        ) { sympauthy, registry ->
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:mfa:write")
            val userToken = signUpAccessToken(registry, "no-return@example.com")

            val rejection = enrollmentRejection(
                sympauthy,
                callerToken,
                ClientMfaEnrollmentInputResource(
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

    @ParameterizedTest(name = "enrollment by a caller lacking users:mfa:write is forbidden on {0}")
    @EnumSource(Database::class)
    fun rejectsCallerWithoutMfaWriteScope(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(clientId, CLIENT_SECRET),
            build = { fixture, registry -> mfaEnrollmentContainer(fixture, registry) },
        ) { sympauthy, registry ->
            // A client-credentials token WITHOUT users:mfa:write — it authenticates the client but lacks the scope.
            val callerToken = clientCredentialsToken(sympauthy, registry)
            val userToken = signUpAccessToken(registry, "no-scope@example.com")

            val rejection = enrollmentRejection(
                sympauthy,
                callerToken,
                ClientMfaEnrollmentInputResource(
                    accessToken = userToken,
                    returnUri = mfaEnrollmentReturnUri(registry),
                    cancelUri = null,
                ),
            )
            assertEquals(403, rejection.status, "a caller without users:mfa:write should be forbidden")
        }
    }

    @ParameterizedTest(name = "enrollment is rejected when MFA is disabled on {0}")
    @EnumSource(Database::class)
    fun rejectsWhenMfaDisabled(database: Database) {
        withCustomContainer(
            database,
            client = Client.confidentialClient(clientId, CLIENT_SECRET),
            build = { fixture, registry -> mfaEnrollmentContainer(fixture, registry, mfaEnabled = false) },
        ) { sympauthy, registry ->
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:mfa:write")

            // The MFA-disabled guard fires before any token introspection, so no end-user token is needed.
            val rejection = enrollmentRejection(
                sympauthy,
                callerToken,
                ClientMfaEnrollmentInputResource(
                    accessToken = "irrelevant-when-mfa-disabled",
                    returnUri = mfaEnrollmentReturnUri(registry),
                    cancelUri = null,
                ),
            )
            assertEquals(400, rejection.status, "enrollment should be refused when MFA is disabled")
            assertTrue(
                rejection.body.contains("mfa_disabled"),
                "should be rejected because MFA is disabled, was: ${rejection.body}",
            )
        }
    }

    @ParameterizedTest(name = "enrollment with an end-user token issued to another client is rejected on {0}")
    @EnumSource(Database::class)
    fun rejectsUserTokenIssuedToAnotherClient(database: Database) {
        // Two clients, each with its own mock frontend: the caller (holding users:mfa:write) and another client
        // whose end-user token we present to the caller's enrollment endpoint.
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
                                    val callerToken = clientCredentialsToken(sympauthy, caller, "users:mfa:write")
                                    val foreignUserToken = signUpAccessToken(other, "eve@example.com")

                                    val rejection = enrollmentRejection(
                                        sympauthy,
                                        callerToken,
                                        ClientMfaEnrollmentInputResource(
                                            accessToken = foreignUserToken,
                                            returnUri = mfaEnrollmentReturnUri(caller),
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


    /** Builds the standard single-client MFA-enrollment container for [registry] (a confidential client). */
    private fun mfaEnrollmentContainer(
        fixture: DatabaseFixture,
        registry: InteractiveFlowRegistry,
        mfaEnabled: Boolean = true,
    ): SympauthyContainer {
        registry.withScopes("openid")
        return fixture.applyTo(
            SympauthyContainer(SympauthyImage.resolve())
                .withConfig(mfaEnrollmentConfig(registry, mfaEnabled))
                .withFlows(registry),
        )
    }

    /** [mfaEnrollmentConfig] for [caller] plus a second, public [other] client with its own flow. */
    private fun twoClientConfig(
        caller: InteractiveFlowRegistry,
        other: InteractiveFlowRegistry,
    ): Map<String, Any> {
        val base = mfaEnrollmentConfig(caller)
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
     * Calls `POST /api/v1/client/mfa/enrollment` expecting a rejection, and captures the HTTP status and error
     * body **inside** the client's context (its response buffer is released once [withApiClient] closes the
     * context, so the body must be read before returning).
     */
    private fun enrollmentRejection(
        sympauthy: SympauthyContainer,
        callerToken: String,
        input: ClientMfaEnrollmentInputResource,
    ): RejectedEnrollment = withApiClient(sympauthy, token = callerToken) { ctx ->
        try {
            ctx.getBean(ClientApi::class.java).startEnrollment1(input).block()
            fail("expected the enrollment request to be rejected, but it succeeded")
        } catch (error: HttpClientResponseException) {
            RejectedEnrollment(error.status.code, error.response.getBody(String::class.java).orElse(""))
        }
    }

    /** The HTTP status and raw error body (carrying the server's `error_code`) of a rejected enrollment call. */
    private data class RejectedEnrollment(val status: Int, val body: String)

    private companion object {
        const val CLIENT_SECRET = "s3cr3t-mfa"
        const val OTHER_CLIENT_ID = "other-app"
        const val PASSWORD = "Str0ngP@ssw0rd!"
    }
}
