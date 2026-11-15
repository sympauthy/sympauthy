package com.sympauthy.it.feature

import com.sympauthy.api.client.api.ClientApi
import com.sympauthy.api.client.api.FlowApi
import com.sympauthy.api.client.model.ClientMfaEnrollmentInputResource
import com.sympauthy.api.client.model.ClientMfaEnrollmentResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.ConfirmDecision
import com.sympauthy.testcontainers.flow.FlowOutcome
import com.sympauthy.testcontainers.flow.FlowStep
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import com.sympauthy.testcontainers.flow.Totp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.URI

/**
 * Feature scenario — **on-demand MFA enrollment started by a client**
 * (`POST /api/v1/client/mfa/enrollment`).
 *
 * This proves the whole standalone (non-authorize) enrollment works end-to-end: a confidential client
 * authenticates with a `client_credentials` token holding `users:mfa:write`, starts an enrollment for one of its
 * signed-in end-users naming no `return_uri`, and the returned `redirect_url` is driven through the state-secured
 * flow API — the `CONFIRM` step then TOTP enrollment — terminating by returning the end-user to the client's
 * `return-uri` with the flow named and the `return_state` it sent. Leaving the enrollment through the
 * confirmation step's `cancel_url` returns the end-user to that same page, saying the flow was cancelled. The
 * sequence is what takes the whole instance: the client API mints the session, the flow API walks it, and the
 * terminal redirect is the enrollment's own. Each refusal this endpoint decides is tested on the class that
 * decides it.
 *
 * Reference: issue [#280](https://github.com/sympauthy/sympauthy/issues/280) (the endpoint),
 * [#285](https://github.com/sympauthy/sympauthy/issues/285) (these tests) and
 * [#709](https://github.com/sympauthy/sympauthy/issues/709) (the return destination and leaving).
 */
@Tag("feature")
class ClientMfaEnrollmentFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "client-initiated MFA enrollment enrolls TOTP and returns to return-uri on {0}")
    @EnumSource(Database::class)
    fun startsEnrollmentAndDrivesItToReturnUri(database: Database) {
        withEnrollmentContainer(database) { sympauthy, registry ->
            val returnUri = mfaEnrollmentReturnUri(registry)
            val enrollment = startEnrollment(sympauthy, registry)

            val flow = registry.newFlow()
                .withConfirmHandler { resource ->
                    assertEquals("enroll_mfa", resource.action(), "confirm should describe the action")
                    assertEquals(clientId, resource.client()?.id(), "confirm should name the client that started it")
                    ConfirmDecision.CONFIRM
                }
                .withTotpEnrollmentHandler { data -> Totp.code(data.secret()) }
            // One page serves every outcome, so the driver is given it as both terminals; the parameters say
            // which outcome it was.
            val result = flow.driveFrom(enrollment.redirectUrl, returnUri, returnUri).drive()

            assertEquals(FlowOutcome.SUCCESS, result.outcome(), "approving confirm + enrolling TOTP should complete")
            assertTrue(
                result.stepTypes().contains(FlowStep.Type.CONFIRM),
                "flow should include a confirm step, was: ${result.stepTypes()}",
            )
            assertTrue(
                result.stepTypes().contains(FlowStep.Type.MFA),
                "flow should include an MFA enrollment step, was: ${result.stepTypes()}",
            )
            assertTrue(
                result.terminalUrl().startsWith(URI.create(returnUri).path),
                "enrollment should return to return-uri, was: ${result.terminalUrl()}",
            )
            assertEquals("mfa_enrollment", result.terminalParam("sympauthy_flow"), "the return should name the flow")
            assertEquals(RETURN_STATE, result.terminalParam("sympauthy_return_state"))
            assertNull(
                result.terminalParam("sympauthy_flow_error"),
                "a completed enrollment should return without an error, was: ${result.terminalUrl()}",
            )
        }
    }

    @ParameterizedTest(name = "leaving a client-initiated MFA enrollment returns to return-uri as cancelled on {0}")
    @EnumSource(Database::class)
    fun leavingEnrollmentReturnsToReturnUriAsCancelled(database: Database) {
        withEnrollmentContainer(database) { sympauthy, registry ->
            val enrollment = startEnrollment(sympauthy, registry)

            val cancelUrl = withApiClient(sympauthy, state = enrollment.state) { ctx ->
                ctx.getBean(FlowApi::class.java).getConfirmation().block()?.cancelUrl
            } ?: fail("the confirmation step should offer a cancel_url")
            val leave = withFlowClient(sympauthy) { it.get(cancelUrl) }

            assertEquals(303, leave.status, "leaving should redirect the browser")
            val location = leave.location ?: fail("leaving should name where the browser goes")
            assertTrue(
                location.startsWith(mfaEnrollmentReturnUri(registry)),
                "leaving should return to return-uri, was: $location",
            )
            assertEquals("mfa_enrollment", queryParam(location, "sympauthy_flow"))
            assertEquals("auth.interactive_flow_session.cancelled", queryParam(location, "sympauthy_flow_error"))
            assertEquals(RETURN_STATE, queryParam(location, "sympauthy_return_state"))
        }
    }

    private fun withEnrollmentContainer(
        database: Database,
        block: (SympauthyContainer, InteractiveFlowRegistry) -> Unit,
    ) = withCustomContainer(
        database,
        client = Client.confidentialClient(clientId, CLIENT_SECRET),
        build = { fixture, registry -> container(fixture, mfaEnrollmentConfig(registry), registry) },
        block = block,
    )

    private fun startEnrollment(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
    ): ClientMfaEnrollmentResource {
        val callerToken = clientCredentialsToken(sympauthy, registry, "users:mfa:write")
        val userToken = signUpAndExchange(sympauthy, registry).accessToken
        return withApiClient(sympauthy, token = callerToken) { ctx ->
            ctx.getBean(ClientApi::class.java).startEnrollment1(
                ClientMfaEnrollmentInputResource(accessToken = userToken, returnState = RETURN_STATE),
            ).block()
        } ?: fail("enrollment init should return a state and redirect_url")
    }

    private companion object {
        const val CLIENT_SECRET = "s3cr3t-mfa"
        const val RETURN_STATE = "xyz"
    }
}
