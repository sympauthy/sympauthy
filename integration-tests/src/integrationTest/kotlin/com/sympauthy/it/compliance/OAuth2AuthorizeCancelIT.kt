package com.sympauthy.it.compliance

import com.sympauthy.api.client.api.FlowApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Compliance scenario — **cancelling an OAuth2 authorization returns the `access_denied` error to the
 * client, and never mints an authorization code.**
 *
 * Risk: when the end-user declines an authorization request, OAuth2 requires the server to hand the
 * user-agent back to the client's `redirect_uri` with `error=access_denied` and the client's `state`
 * echoed — and, crucially, to *not* issue an authorization code. A server that dropped the user on an
 * error page (or, worse, still minted a code) would both break clients that rely on the standard error
 * channel and risk handing out a credential for a flow the user rejected.
 *
 * This starts a real authorization request, captures the signed internal state from the `303` to the
 * sign-in page, follows the `cancel_url` the sign-in step offers, and asserts it redirects to the client
 * `redirect_uri` carrying `error=access_denied` with the client `state` echoed, **no `code`**, and none of
 * the parameters a return destination of this server's own receives, on each supported database.
 *
 * Source: [RFC 6749 §4.1.2.1 (Authorization Code Grant — Error Response)](
 * https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1).
 */
@Tag("compliance")
class OAuth2AuthorizeCancelIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "cancelling an authorization returns access_denied with no code on {0}")
    @EnumSource(Database::class)
    fun cancellingAuthorizationReturnsAccessDenied(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val internalState = withFlowClient(sympauthy) { flow ->
                val authorize = flow.get(authorizeUrl(sympauthy, registry))
                assertEquals(303, authorize.status, "authorize should redirect to the first flow step")
                internalState(authorize)
            }

            val cancelUrl = withApiClient(sympauthy, state = internalState) { ctx ->
                ctx.getBean(FlowApi::class.java).getSignInConfiguration().block()?.cancelUrl
            } ?: error("the sign-in step should offer a cancel_url")
            val leave = withFlowClient(sympauthy) { it.get(cancelUrl) }
            assertEquals(303, leave.status, "leaving should redirect the browser")
            val redirect = leave.location ?: error("leaving should name where the browser goes")

            assertTrue(
                redirect.startsWith(registry.redirectUri()),
                "cancel should redirect to the client redirect_uri, was: $redirect",
            )
            assertEquals(
                "access_denied", queryParam(redirect, "error"),
                "a cancelled authorization must return error=access_denied, was: $redirect",
            )
            assertEquals(
                CLIENT_STATE, queryParam(redirect, "state"),
                "the client state must be echoed back, was: $redirect",
            )
            assertNull(
                queryParam(redirect, "code"),
                "a cancelled authorization must not mint an authorization code, was: $redirect",
            )
            assertNull(
                queryParam(redirect, "sympauthy_flow"),
                "an authorization answers through the protocol alone, was: $redirect",
            )
        }
    }
}
