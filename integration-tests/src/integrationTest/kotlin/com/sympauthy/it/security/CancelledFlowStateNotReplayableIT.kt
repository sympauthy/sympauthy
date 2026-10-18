package com.sympauthy.it.security

import com.sympauthy.api.client.api.FlowApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **once a flow is cancelled its signed state is spent: it can no longer be replayed
 * to drive the flow.**
 *
 * Risk: the signed internal state travels in URLs and the `Authorization: State` header, so it can leak
 * (browser history, logs, a shared link). Cancellation must make the session terminal, so a captured
 * state cannot be replayed afterwards to resurrect the flow and reach a code-minting step. If a cancelled
 * session could still be advanced, an attacker holding the state could complete an authorization the user
 * had explicitly declined.
 *
 * This starts an authorization, cancels it, then replays the same state against a state-secured step that
 * requires an ongoing session (`GET /api/v1/flow/mfa/challenge`) and asserts it is rejected with
 * `400 ctrl.flow.not_ongoing`, on each supported database.
 *
 * Source: [OAuth 2.1 §7.1 (Access Token / credential handling)](
 * https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-7.1) — cancellation is a terminal
 * state; the flow's own single-use / terminal-session model enforces it.
 */
@Tag("security")
class CancelledFlowStateNotReplayableIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "the state of a cancelled flow cannot be replayed on {0}")
    @EnumSource(Database::class)
    fun cancelledStateCannotBeReplayed(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val internalState = withFlowClient(sympauthy) { flow ->
                internalState(flow.get(authorizeUrl(sympauthy, registry)))
            }

            withApiClient(sympauthy, state = internalState) { ctx ->
                val flowApi = ctx.getBean(FlowApi::class.java)
                flowApi.cancel().block()

                val message = "replaying a cancelled flow's state must be rejected as no longer ongoing"
                val replay = assertThrows<HttpClientResponseException>(message) {
                    flowApi.getChallengeRedirect().block()
                }
                assertError(replay, 400, "ctrl.flow.not_ongoing", message)
            }
        }
    }
}
