package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
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
 * Every exchange goes through the generated client with the verifier of the code's own challenge, so a
 * refusal is the code's lifetime and nothing else the token endpoint checks. The first scenario gives codes
 * a lifetime of a few seconds, signs a person up and exchanges the code once that lifetime has passed: it is
 * refused with `invalid_grant`. The second gives the flow a few seconds and codes their default minute, and
 * exchanges a code once the session that issued it has expired: tokens are issued. The third has a
 * confidential client exchange its code, waits past the code's lifetime and exchanges it again: the second
 * exchange is refused with `invalid_grant`, and the tokens of the first still introspect as active — a code
 * past its lifetime is refused as expired, and revokes nothing as a replay inside it would. Each runs on
 * every supported database.
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
            val result = signUp(registry)

            Thread.sleep((CODE_LIFETIME_SECONDS + 1) * 1000)

            assertExchangeRefused(
                sympauthy, registry, result, 400, "invalid_grant", "a code past its lifetime must be refused",
            )
        }
    }

    @ParameterizedTest(name = "a code is exchanged once the session that issued it has expired on {0}")
    @EnumSource(Database::class)
    fun codeOutlivesTheSessionThatIssuedIt(database: Database) {
        val shortFlows = mapOf("auth" to mapOf("interactive-flow" to mapOf("expiration" to "${FLOW_SECONDS}s")))

        withContainer(database, shortFlows) { sympauthy, registry ->
            val result = signUp(registry)

            // Past the time the flow was allowed from its start, and well inside the code's own minute.
            Thread.sleep((FLOW_SECONDS + 1) * 1000)

            checkNotNull(exchange(sympauthy, registry, result).accessToken) {
                "a code must be exchangeable after its session expired"
            }
        }
    }

    @ParameterizedTest(name = "a spent code presented past its lifetime revokes nothing on {0}")
    @EnumSource(Database::class)
    fun spentCodePastItsLifetimeRevokesNothing(database: Database) {
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(database, SHORT_CODES, confidentialClient) { sympauthy, registry ->
            val result = signUp(registry)
            val accessToken = checkNotNull(exchange(sympauthy, registry, result).accessToken) {
                "the first exchange should return an access token"
            }

            Thread.sleep((CODE_LIFETIME_SECONDS + 1) * 1000)

            assertExchangeRefused(
                sympauthy, registry, result, 400, "invalid_grant", "a code past its lifetime must be refused",
            )
            assertEquals(
                true, introspect(sympauthy, accessToken, registry).active,
                "a code presented past its lifetime must not revoke what its first exchange was issued",
            )
        }
    }

    private companion object {
        const val CLIENT_SECRET = "authorization-code-lifetime-secret-value"

        /** Long enough for a client to exchange a code it was just redirected with, on a slow runner too. */
        const val CODE_LIFETIME_SECONDS = 10L

        /** Long enough for the mock frontend to sign a person up, on a slow runner too. */
        const val FLOW_SECONDS = 30L

        val SHORT_CODES = mapOf(
            "auth" to mapOf("authorization-code" to mapOf("expiration" to "${CODE_LIFETIME_SECONDS}s")),
        )
    }
}
