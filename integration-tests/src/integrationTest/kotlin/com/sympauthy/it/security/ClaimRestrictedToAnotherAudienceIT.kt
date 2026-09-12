package com.sympauthy.it.security

import com.sympauthy.api.client.api.OpenidApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.it.SympauthyImage
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **a claim restricted to one audience must not reach a client of another.**
 *
 * Risk: `claims.<id>.audience` is the whole of what keeps one audience's applications from learning what an
 * operator meant only another's to know. Both surfaces that publish claims to a client — the `id_token` and
 * `/userinfo` — read every claim collected against the subject and then filter, so a filter that does not run
 * hands a storefront application the claim restricted to billing. Consent stops nothing here: the person
 * agreed to the scope the claim sits under, which is what makes the restriction the only check left.
 *
 * Two audiences are configured and the client belongs to `default`, the one its template names. `name` is
 * restricted to no audience and `nickname` to `billing`, and both are required, so the sign-up collects both.
 * The scenario asserts the `id_token` from the code exchange and the `/userinfo` response read with its
 * access token each carry `name` and not `nickname`, on each supported database.
 *
 * Issue: [#454](https://github.com/sympauthy/sympauthy/issues/454).
 */
@Tag("security")
class ClaimRestrictedToAnotherAudienceIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a claim restricted to another audience reaches no client on {0}")
    @EnumSource(Database::class)
    fun claimRestrictedToAnotherAudienceReachesNoClient(database: Database) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.publicClient(clientId)).withScopes(*SCOPES).use { registry ->
                fixture.applyTo(
                    SympauthyContainer(SympauthyImage.resolve())
                        .withConfig(twoAudienceConfig(registry))
                        .withFlows(registry),
                ).use { sympauthy ->
                    withStartedContainer(sympauthy) { publishesOnlyItsOwnAudiencesClaims(it, registry) }
                }
            }
        }
    }

    private fun publishesOnlyItsOwnAudiencesClaims(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
    ) {
        val tokens = registry.newFlow()
            .withSignUpHandler { mapOf("email" to EMAIL, "password" to PASSWORD) }
            .withClaimsHandler { requested -> requested.associate { it.id() to COLLECTED.getValue(it.id()) } }
            .run()
            .exchange()

        val idToken = checkNotNull(tokens.idToken()) { "the openid scope should yield an id_token" }
        val claims = verifyIdTokenSignature(sympauthy, idToken)

        assertEquals(NAME, claims.getStringClaim("name"), "a claim restricted to no audience reaches every client")
        assertNull(
            claims.getClaim("nickname"),
            "the id_token of a client in 'default' must not carry a claim restricted to 'billing'",
        )

        val userInfo = withApiClient(sympauthy, token = tokens.accessToken()) { ctx ->
            ctx.getBean(OpenidApi::class.java).getUserInfo().block()
        }
        requireNotNull(userInfo) { "userinfo returned an empty body" }

        assertEquals(NAME, userInfo.name, "a claim restricted to no audience reaches every client")
        assertNull(
            userInfo.nickname,
            "userinfo read with a token of a client in 'default' must not carry a claim restricted to 'billing'",
        )
    }

    private companion object {

        const val EMAIL = "ada@example.com"
        const val PASSWORD = "Str0ngP@ssw0rd!"
        const val NAME = "Ada Lovelace"
        const val NICKNAME = "Ada"

        val SCOPES = arrayOf("openid", "profile")

        /** The value the sign-up collects for each claim the flow asks for, by claim id. */
        val COLLECTED: Map<String, String> = mapOf(
            "email" to EMAIL,
            "name" to NAME,
            "nickname" to NICKNAME,
        )

        /**
         * Password auth with an email identifier, a second audience beside the shipped `default`, and the two
         * `profile` claims the scenario turns on — `nickname` restricted to `billing`, `name` to nothing. The
         * public client names no audience, so it takes `default` from `templates.clients.default`.
         */
        fun twoAudienceConfig(registry: InteractiveFlowRegistry): Map<String, Any> = mapOf(
            "audiences" to mapOf(
                "billing" to mapOf("token-audience" to "https://billing.example.com"),
            ),
            "auth" to mapOf(
                "by-password" to mapOf("enabled" to true),
                "identifier-claims" to listOf("email"),
            ),
            "claims" to mapOf(
                "email" to mapOf("enabled" to true),
                "name" to mapOf("enabled" to true, "required" to true),
                "nickname" to mapOf("enabled" to true, "required" to true, "audience" to "billing"),
            ),
            "clients" to mapOf(
                registry.clientId() to mapOf(
                    "public" to true,
                    "authorizationFlow" to registry.flowId(),
                    "allowed-grant-types" to listOf("authorization_code"),
                    "allowed-scopes" to SCOPES.toList(),
                    "allowed-redirect-uris" to listOf(registry.redirectUri()),
                ),
            ),
        )
    }
}
