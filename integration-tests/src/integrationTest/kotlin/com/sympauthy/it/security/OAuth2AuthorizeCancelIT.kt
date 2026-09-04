package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **cancelling an OAuth2 authorization returns the `access_denied` error to the
 * client, and never mints an authorization code.**
 *
 * Risk: when the end-user declines an authorization request, OAuth2 requires the server to hand the
 * user-agent back to the client's `redirect_uri` with `error=access_denied` and the client's `state`
 * echoed — and, crucially, to *not* issue an authorization code. A server that dropped the user on an
 * error page (or, worse, still minted a code) would both break clients that rely on the standard error
 * channel and risk handing out a credential for a flow the user rejected.
 *
 * This starts a real authorization request, captures the signed internal state from the `303` to the
 * sign-in page, cancels the flow via `POST /api/v1/flow/cancel`, and asserts the returned redirect is
 * the client `redirect_uri` carrying `error=access_denied` with the client `state` echoed and **no
 * `code`**, on each supported database.
 *
 * Source: [RFC 6749 §4.1.2.1 (Authorization Code Grant — Error Response)](
 * https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1).
 */
@Tag("security")
class OAuth2AuthorizeCancelIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "cancelling an authorization returns access_denied with no code on {0}")
    @EnumSource(Database::class)
    fun cancellingAuthorizationReturnsAccessDenied(database: Database) {
        withContainer(database) { sympauthy, registry ->
            withFlowClient(sympauthy) { flow ->
                // The state carried on the redirect is signed, and is what authenticates every flow
                // endpoint called below.
                val authorize = flow.get(authorizeUrl(sympauthy, registry))
                assertEquals(303, authorize.status, "authorize should redirect to the first flow step")
                val internalState = queryParam(authorize.location ?: error("authorize 303 had no Location"), "state")
                    ?: error("authorize redirect did not carry a state: ${authorize.location}")

                val cancel = flow.cancel(internalState)
                assertEquals(200, cancel.status, "cancel should succeed, body=${cancel.body}")
                val redirect = cancel.fields["redirect_url"]
                    ?: error("cancel response had no redirect_url: ${cancel.body}")

                assertTrue(
                    redirect.startsWith(registry.redirectUri()),
                    "cancel should redirect to the client redirect_uri, was: $redirect",
                )
                assertEquals(
                    "access_denied", queryParam(redirect, "error"),
                    "a cancelled authorization must return error=access_denied, was: $redirect",
                )
                assertEquals(
                    "integration-test-state", queryParam(redirect, "state"),
                    "the client state must be echoed back, was: $redirect",
                )
                assertNull(
                    queryParam(redirect, "code"),
                    "a cancelled authorization must not mint an authorization code, was: $redirect",
                )
            }
        }
    }
}
