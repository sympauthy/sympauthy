package com.sympauthy.it.feature

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.FlowException
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **a deployment may name several identifier claims, and a person signs in with any
 * one of them.**
 *
 * `auth.identifier-claims` is a list. With `[email, preferred_username]` configured, an account collects
 * both at sign-up and either value reaches it afterwards: the person types one login, and the server
 * matches that single value against every configured claim. Both sign-ins land on the *same* account,
 * which is what the `sub` of each id token asserts here — the token is verified against the server's own
 * key set, so it is the server's answer and not the driver's.
 *
 * No unit test reaches this. Which claim a login matched is decided in a query, the account it resolves
 * to is proved by a signed token minted at the end of a second complete flow, and the sign-up that
 * collected both claims is a third — three flows through the live instance, against each database.
 *
 * The rejection below is the other half of the same rule, and the reason the guarantee above is worth
 * anything: a value belongs to one account across the whole set rather than within one claim. Allowing a
 * second account to take, as its username, the address the first signs in with would leave that address
 * matching a row of each — and the server answers one of them, so the owner of the address can be signed
 * in against the other account.
 *
 * Issue: [#479](https://github.com/sympauthy/sympauthy/issues/479), and
 * [`docs/identifier-claims.md`](https://github.com/sympauthy/sympauthy/blob/main/docs/identifier-claims.md).
 */
@Tag("feature")
class MultipleIdentifierClaimsFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "either identifier claim reaches the same account on {0}")
    @EnumSource(Database::class)
    fun signsInWithEitherIdentifierClaim(database: Database) {
        withContainer(database, extraConfig = twoIdentifierClaims(), scopes = SCOPES) { sympauthy, registry ->
            val signedUp = registry.newFlow()
                .withSignUpHandler { mapOf("email" to EMAIL, "preferred_username" to USERNAME, "password" to PASSWORD) }
                .run()
            val account = subjectOf(sympauthy, signedUp.exchange().idToken())

            val byEmail = signIn(registry, EMAIL)
            val byUsername = signIn(registry, USERNAME)

            assertEquals(account, subjectOf(sympauthy, byEmail), "signing in with the email reaches the account")
            assertEquals(
                account, subjectOf(sympauthy, byUsername),
                "signing in with the username reaches the same account, not a second one",
            )
        }
    }

    @ParameterizedTest(name = "a second account cannot take an identifier another holds on {0}")
    @EnumSource(Database::class)
    fun refusesAnIdentifierAnotherAccountHoldsUnderAnotherClaim(database: Database) {
        withContainer(database, extraConfig = twoIdentifierClaims(), scopes = SCOPES) { _, registry ->
            registry.newFlow()
                .withSignUpHandler { mapOf("email" to EMAIL, "preferred_username" to USERNAME, "password" to PASSWORD) }
                .run()

            // Offered as a username, the address the first account signs in with. Nothing about this
            // account collides claim by claim — its own email is free — which is why the check has to
            // range over the whole set.
            val crossing = registry.newFlow()
                .withSignUpHandler {
                    mapOf("email" to OTHER_EMAIL, "preferred_username" to EMAIL, "password" to PASSWORD)
                }

            assertThrows<FlowException>("the sign-up must not complete") { crossing.run() }

            // The address still signs the first account in, and its password is still the first's.
            assertNotNull(signIn(registry, EMAIL), "the address is untouched")
        }
    }

    /**
     * The base configuration's one identifier claim, plus a second of another type. `preferred_username`
     * is a `profile` claim, so the client is allowed and defaulted to that scope as well as `openid` —
     * the base configuration allows `openid` alone, and a flow asking for more than the client allows is
     * refused before any step runs.
     */
    private fun twoIdentifierClaims(): Map<String, Any> = mapOf(
        "auth" to mapOf("identifier-claims" to listOf("email", "preferred_username")),
        "claims" to mapOf("preferred_username" to mapOf("enabled" to true)),
        "clients" to mapOf(
            clientId to mapOf("allowed-scopes" to SCOPES, "default-scopes" to SCOPES),
        ),
    )

    /** The id token of a complete sign-in with [login], as the server signed it. */
    private fun signIn(registry: InteractiveFlowRegistry, login: String): String =
        registry.newFlow()
            .withSignInHandler { Credentials.of(login, PASSWORD) }
            .run()
            .exchange()
            .idToken()
            ?: error("the openid scope should yield an id_token for '$login'")

    private fun subjectOf(sympauthy: SympauthyContainer, idToken: String?): String =
        verifyIdTokenSignature(sympauthy, requireNotNull(idToken) { "no id_token" }).subject

    private companion object {

        const val EMAIL = "ada@example.com"
        const val USERNAME = "ada.lovelace"
        const val OTHER_EMAIL = "grace@example.com"
        const val PASSWORD = "Str0ngP@ssw0rd!"

        val SCOPES = listOf("openid", "profile")

    }
}
