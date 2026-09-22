package com.sympauthy.it.feature

import com.sympauthy.api.client.api.OpenidApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.FlowException
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **one address is one identity, however the person spelled it.**
 *
 * An `email` identifier claim is stored folded and trimmed, so an account signed up as
 * `Ada@Example.COM` is reached by `ADA@EXAMPLE.COM` and by the same address padded with spaces, and a
 * second account cannot be opened on any other spelling of it. Every comparison this server makes on an
 * identifier is an exact one, so what makes two spellings meet is the single spelling that got stored.
 *
 * No unit test reaches this. What is stored is written by one flow and read back through the userinfo
 * endpoint with that flow's own access token; that the folded row is what a later login matches is
 * decided in a query, and that a second sign-up loses to it is a third complete flow — each against
 * both databases.
 *
 * Issue: [#488](https://github.com/sympauthy/sympauthy/issues/488), and
 * [`docs/identifier-claims.md`](https://github.com/sympauthy/sympauthy/blob/main/docs/identifier-claims.md).
 */
@Tag("feature")
class IdentifierSpellingFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "an address reaches its account in any spelling on {0}")
    @EnumSource(Database::class)
    fun signsInWithAnySpellingOfTheAddress(database: Database) {
        withContainer(database, extraConfig = emailScope(), scopes = SCOPES) { sympauthy, registry ->
            val tokens = registry.newFlow()
                .withSignUpHandler { mapOf("email" to TYPED, "password" to PASSWORD) }
                .run()
                .exchange()
            val account = subjectOf(sympauthy, tokens.idToken())

            val stored = withApiClient(sympauthy, token = tokens.accessToken()) { ctx ->
                ctx.getBean(OpenidApi::class.java).getUserInfo().block()
            }
            requireNotNull(stored) { "userinfo returned an empty body" }

            assertEquals(STORED, stored.email, "the address is stored folded and trimmed, not as it was typed")
            assertEquals(
                account, subjectOf(sympauthy, signIn(registry, SHOUTED)),
                "a third spelling of the address reaches the account that owns it",
            )
            assertEquals(
                account, subjectOf(sympauthy, signIn(registry, "  $STORED  ")),
                "a login padded with whitespace reaches the same account",
            )
        }
    }

    @ParameterizedTest(name = "a second account cannot take the address in another spelling on {0}")
    @EnumSource(Database::class)
    fun refusesASecondAccountOverAnotherSpelling(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val first = registry.newFlow()
                .withSignUpHandler { mapOf("email" to TYPED, "password" to PASSWORD) }
                .run()
            val account = subjectOf(sympauthy, first.exchange().idToken())

            // The same address in the spelling the first account did not type. Its password differs, so
            // an account that did come out of it could not be mistaken for the one that already exists.
            val second = registry.newFlow()
                .withSignUpHandler { mapOf("email" to STORED, "password" to OTHER_PASSWORD) }

            assertThrows<FlowException>("the sign-up must not complete") { second.run() }

            assertEquals(
                account, subjectOf(sympauthy, signIn(registry, STORED, PASSWORD)),
                "the address still reaches the account that owns it, under its own password",
            )
        }
    }

    /**
     * The `email` scope on top of the base configuration, so that the userinfo endpoint publishes the
     * address back. The base configuration allows the client `openid` alone and defaults it to the same,
     * so both lists are overridden with the pair.
     */
    private fun emailScope(): Map<String, Any> = mapOf(
        "clients" to mapOf(
            clientId to mapOf("allowed-scopes" to SCOPES, "default-scopes" to SCOPES),
        ),
    )

    /** The id token of a complete sign-in with [login], as the server signed it. */
    private fun signIn(registry: InteractiveFlowRegistry, login: String, password: String = PASSWORD): String =
        registry.newFlow()
            .withSignInHandler { Credentials.of(login, password) }
            .run()
            .exchange()
            .idToken()
            ?: error("the openid scope should yield an id_token for '$login'")

    private fun subjectOf(sympauthy: SympauthyContainer, idToken: String?): String =
        verifyIdTokenSignature(sympauthy, requireNotNull(idToken) { "no id_token" }).subject

    private companion object {

        /** The address as the person typed it at sign-up, in mixed case and padded. */
        const val TYPED = " Ada@Example.COM "
        const val STORED = "ada@example.com"
        const val SHOUTED = "ADA@EXAMPLE.COM"
        const val PASSWORD = "Str0ngP@ssw0rd!"
        const val OTHER_PASSWORD = "0therP@ssw0rd!"

        val SCOPES = listOf("openid", "email")
    }
}
