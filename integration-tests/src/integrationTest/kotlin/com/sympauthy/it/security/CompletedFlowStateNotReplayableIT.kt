package com.sympauthy.it.security

import com.sympauthy.api.client.api.FlowApi
import com.sympauthy.api.client.model.SignUpInputResource
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **once a flow has handed its authorization code to the client, its signed state
 * hands it out no more: a repeated flow request sends the browser to the error page, which says the
 * authorization already completed.**
 *
 * Risk: the signed internal state travels in URLs and the `Authorization: State` header, so it can leak
 * (browser history, logs, a shared link), and it outlives the flow. A state that still led to the client
 * redirect would hand the code to whoever replays it, and send the client a code it already exchanged.
 * Answering a repeated request with a protocol error instead leaves the flow pages with nowhere to send a
 * person who reloaded or submitted twice.
 *
 * This signs a person up, which completes the authorization and hands the code to that request, then
 * submits the sign-up again and reloads the sign-in step with the same state. Both answer a `redirect_url`
 * to the error page and carry no code, and the error page answers
 * `auth.interactive_flow_session.already_completed` — the session was left completed rather than failed —
 * on each supported database.
 *
 * Source: [RFC 6749 §10.5 (Authorization Codes)](https://datatracker.ietf.org/doc/html/rfc6749#section-10.5)
 * and [#536](https://github.com/sympauthy/sympauthy/issues/536).
 */
@Tag("security")
class CompletedFlowStateNotReplayableIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "the state of a completed flow does not hand its code out again on {0}")
    @EnumSource(Database::class)
    fun completedStateDoesNotHandOutTheCodeAgain(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val internalState = withFlowClient(sympauthy) { flow ->
                internalState(flow.get(authorizeUrl(sympauthy, registry)))
            }
            val errorPage = "${registry.frontendUrl()}/error"

            withApiClient(sympauthy, state = internalState) { ctx ->
                val flowApi = ctx.getBean(FlowApi::class.java)

                val clientRedirect = flowApi.signUp(signUpInput()).block()?.redirectUrl
                assertNotNull(clientRedirect, "the sign-up should complete the flow")
                assertNotNull(queryParam(clientRedirect!!, "code"), "the client redirect should carry the code")

                val resubmitted = flowApi.signUp(signUpInput()).block()?.redirectUrl
                val reloaded = flowApi.getSignInConfiguration().block()?.redirectUrl
                for (redirect in listOf(resubmitted, reloaded)) {
                    val repeated = checkNotNull(redirect) { "a repeated request should be answered a redirect_url" }
                    assertTrue(repeated.startsWith(errorPage), "expected the error page, was: $repeated")
                    assertNull(queryParam(repeated, "code"), "the code must not be handed out again: $repeated")
                }

                val error = checkNotNull(flowApi.getError().block()) { "the error page should be answered" }
                assertEquals("auth.interactive_flow_session.already_completed", error.errorCode)
                assertNull(error.redirectUrl, "the error page must not redirect to itself")
            }
        }
    }

    /**
     * The sign-up [credentials] as the generated resource carries them. It is a map of the identifier claims
     * beside a declared password, and serializes as the map alone, so the password goes in it too.
     */
    private fun signUpInput() = SignUpInputResource(password = credentials().getValue("password"))
        .apply { putAll(credentials()) }
}
