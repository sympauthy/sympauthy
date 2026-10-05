package com.sympauthy.it.feature

import com.sympauthy.api.client.api.ClientApi
import com.sympauthy.api.client.model.ClientMfaEnrollmentInputResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.flow.ConfirmDecision
import com.sympauthy.testcontainers.flow.FlowOutcome
import com.sympauthy.testcontainers.flow.FlowStep
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import com.sympauthy.testcontainers.flow.Totp
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **on-demand MFA enrollment started by a client**
 * (`POST /api/v1/client/mfa/enrollment`).
 *
 * This proves the whole standalone (non-authorize) enrollment works end-to-end: a confidential client
 * authenticates with a `client_credentials` token holding `users:mfa:write`, starts an enrollment for one of its
 * signed-in end-users, and the returned `redirect_url` is driven through the state-secured flow API — the `CONFIRM`
 * step then TOTP enrollment — terminating by redirecting the end-user back to the client-provided `return_uri`.
 * The sequence is what takes the whole instance: the client API mints the session, the flow API walks it, and
 * the terminal redirect is the enrollment's own. Each refusal this endpoint decides is tested on the class that
 * decides it.
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
            build = { fixture, registry -> container(fixture, mfaEnrollmentConfig(registry), registry) },
        ) { sympauthy, registry ->
            val callerToken = clientCredentialsToken(sympauthy, registry, "users:mfa:write")
            val userToken = signUpAccessToken(registry, DEFAULT_EMAIL)

            val returnUri = mfaEnrollmentReturnUri(registry)
            val cancelUri = mfaEnrollmentCancelUri(registry)
            val enrollment = withApiClient(sympauthy, token = callerToken) { ctx ->
                ctx.getBean(ClientApi::class.java).startEnrollment1(
                    ClientMfaEnrollmentInputResource(accessToken = userToken, returnUri = returnUri, cancelUri = cancelUri),
                ).block()
            } ?: fail("enrollment init should return a state and redirect_url")

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

    /** Signs up a fresh end-user through [registry]'s flow and returns their access token. */
    private fun signUpAccessToken(registry: InteractiveFlowRegistry, email: String): String {
        val token = signUpAndExchange(registry, email).accessToken()
        assertNotNull(token, "sign-up should yield an end-user access token")
        return token
    }

    private companion object {
        const val CLIENT_SECRET = "s3cr3t-mfa"
    }
}
