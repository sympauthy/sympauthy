package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.client.SympauthyApiException
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **an authorization code is exchangeable for a lifetime of its own, counted from when
 * it was issued, and not for whatever is left of the interactive flow session that issued it.**
 *
 * Risk: a code is what leaks — into a log, a browser's history, a `Referer` — so how long it can be
 * exchanged is how long a leaked one is worth something. RFC 6749 §4.1.2 asks for a short lifetime. A code
 * that lived as long as its session would have that lifetime depend on how fast the person signed in, and
 * a code whose exchange read its session would die with it however recently it was issued.
 *
 * The first scenario gives codes a lifetime of a few seconds, signs a person up and presents the code, with
 * the verifier of its own challenge, once that lifetime has passed: it is refused with `invalid_grant`,
 * every other check passing. The second gives the flow a few seconds and codes their default minute, and
 * exchanges a code once the session that issued it has expired: tokens are issued. The third has a
 * confidential client exchange its code, waits past the code's lifetime and exchanges it again: the second
 * exchange is refused, and the tokens of the first still introspect as active — a code past its lifetime is
 * refused as expired, and revokes nothing as a replay inside it would. Each runs on every supported
 * database.
 *
 * Source: [sympauthy#590](https://github.com/sympauthy/sympauthy/issues/590), and
 * [RFC 6749 §4.1.2](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2).
 */
@Tag("security")
class AuthorizationCodeLifetimeIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a code is refused once its lifetime has passed on {0}")
    @EnumSource(Database::class)
    fun codePastItsLifetimeIsRefused(database: Database) {
        withContainer(database, SHORT_CODES) { sympauthy, registry ->
            val pkce = generatePkce()
            val code = signUpWithPkceForCode(sympauthy, registry, pkce)

            Thread.sleep((CODE_LIFETIME_SECONDS + 1) * 1000)

            val response = exchangeCode(sympauthy, registry, code, mapOf("code_verifier" to pkce.verifier))
            assertOAuthError(response, 400, "invalid_grant", "a code past its lifetime must be refused")
        }
    }

    @ParameterizedTest(name = "a code is exchanged once the session that issued it has expired on {0}")
    @EnumSource(Database::class)
    fun codeOutlivesTheSessionThatIssuedIt(database: Database) {
        val shortFlows = mapOf("auth" to mapOf("interactive-flow" to mapOf("expiration" to "${FLOW_SECONDS}s")))

        withContainer(database, shortFlows) { sympauthy, registry ->
            val pkce = generatePkce()
            val code = signUpWithPkceForCode(sympauthy, registry, pkce)

            // Past the time the flow was allowed from its start, and well inside the code's own minute.
            Thread.sleep((FLOW_SECONDS + 1) * 1000)

            val response = exchangeCode(sympauthy, registry, code, mapOf("code_verifier" to pkce.verifier))
            assertEquals(
                200, response.statusCode(),
                "a code must be exchangeable after its session expired, body=${response.body()}",
            )
        }
    }

    @ParameterizedTest(name = "a spent code presented past its lifetime revokes nothing on {0}")
    @EnumSource(Database::class)
    fun spentCodePastItsLifetimeRevokesNothing(database: Database) {
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, SHORT_CODES, confidentialClient) { sympauthy, registry ->
            val result = signUp(registry)
            val accessToken = checkNotNull(result.exchange().accessToken()) {
                "the first exchange should return an access token"
            }

            Thread.sleep((CODE_LIFETIME_SECONDS + 1) * 1000)

            assertThrows<SympauthyApiException>("a code past its lifetime must be refused") { result.exchange() }
            assertEquals(
                true, introspect(sympauthy, accessToken, registry).active,
                "a code presented past its lifetime must not revoke what its first exchange was issued",
            )
        }
    }

    /** The code a sign-up from an authorization request carrying [pkce]'s challenge yields. */
    private fun signUpWithPkceForCode(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        pkce: Pkce,
    ): String = checkNotNull(signUpWithPkce(sympauthy, registry, pkce).terminalParam("code")) {
        "expected an authorization code from sign-up"
    }

    private companion object {
        const val CLIENT_SECRET = "authorization-code-lifetime-secret-value"

        /** Long enough for a client to exchange a code it was just redirected with, and no longer. */
        const val CODE_LIFETIME_SECONDS = 5L

        /** Long enough for the mock frontend to sign a person up. */
        const val FLOW_SECONDS = 10L

        val SHORT_CODES = mapOf(
            "auth" to mapOf("authorization-code" to mapOf("expiration" to "${CODE_LIFETIME_SECONDS}s")),
        )
    }
}
