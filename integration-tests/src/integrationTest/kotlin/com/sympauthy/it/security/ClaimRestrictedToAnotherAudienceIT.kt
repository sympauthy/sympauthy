package com.sympauthy.it.security

import com.sympauthy.api.client.api.OpenidApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **a claim collected for one audience must not reach a client of another.**
 *
 * Risk: `claims.<id>.audience` is the whole of what keeps one audience's applications from learning what an
 * operator meant only another's to know. Both surfaces that publish claims to a client — the `id_token` and
 * `/userinfo` — read every claim collected against the subject and then filter, so a filter that does not run
 * hands a storefront application the claim restricted to billing. Consent stops nothing here: the person
 * agreed to the scope the claim sits under, which is what makes the restriction the only check left. One user
 * pool is shared by every audience, so the two are one sign-in apart.
 *
 * Two audiences are configured with a public client apiece. `name` is restricted to no audience and `nickname`
 * to `billing`, and both are required. The person signs up through the billing client, whose flow collects
 * both — and whose own `id_token` carries both, which is what proves the claim was stored rather than quietly
 * dropped. They then sign in through the storefront client, and the `id_token` it is issued and the
 * `/userinfo` it reads must carry `name` and not `nickname`, on each supported database.
 *
 * Issue: [#454](https://github.com/sympauthy/sympauthy/issues/454).
 */
@Tag("security")
class ClaimRestrictedToAnotherAudienceIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a claim collected for another audience reaches no client on {0}")
    @EnumSource(Database::class)
    fun claimCollectedForAnotherAudienceReachesNoClient(database: Database) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(Client.publicClient(BILLING_CLIENT_ID))
                .withFlowId(BILLING_FLOW_ID).withScopes(*SCOPES).use { billing ->
                    InteractiveFlowRegistry.forClient(Client.publicClient(OWN_CLIENT_ID))
                        .withFlowId(OWN_FLOW_ID).withScopes(*SCOPES).use { storefront ->
                            container(
                                fixture, twoAudienceConfig(billing, storefront), billing, storefront,
                            ).use { sympauthy ->
                                withStartedContainer(sympauthy) {
                                    publishesOnlyItsOwnAudiencesClaims(it, billing, storefront)
                                }
                            }
                        }
                }
        }
    }

    private fun publishesOnlyItsOwnAudiencesClaims(
        sympauthy: SympauthyContainer,
        billing: InteractiveFlowRegistry,
        storefront: InteractiveFlowRegistry,
    ) {
        val billingTokens = billing.newFlow()
            .withSignUpHandler { credentials(EMAIL) }
            .withClaimsHandler { requested -> requested.associate { it.id() to COLLECTED.getValue(it.id()) } }
            .run()
            .exchange()

        val billingClaims = verifyIdTokenSignature(sympauthy, requireIdToken(billingTokens.idToken()))
        assertEquals(
            NICKNAME,
            billingClaims.getStringClaim("nickname"),
            "the audience the claim is restricted to is told it, or the rest of this proves nothing",
        )

        val storefrontTokens = storefront.newFlow()
            .withSignInHandler { Credentials.of(EMAIL, DEFAULT_PASSWORD) }
            .run()
            .exchange()

        val storefrontClaims = verifyIdTokenSignature(sympauthy, requireIdToken(storefrontTokens.idToken()))
        assertEquals(
            NAME,
            storefrontClaims.getStringClaim("name"),
            "a claim restricted to no audience reaches every client",
        )
        assertNull(
            storefrontClaims.getClaim("nickname"),
            "the id_token of a client in 'default' must not carry a claim restricted to 'billing'",
        )

        val userInfo = withApiClient(sympauthy, token = storefrontTokens.accessToken()) { ctx ->
            ctx.getBean(OpenidApi::class.java).getUserInfo().block()
        }
        requireNotNull(userInfo) { "userinfo returned an empty body" }

        assertEquals(NAME, userInfo.name, "a claim restricted to no audience reaches every client")
        assertNull(
            userInfo.nickname,
            "userinfo read with a token of a client in 'default' must not carry a claim restricted to 'billing'",
        )
    }

    /**
     * Password auth with an email identifier, a second audience beside the shipped `default`, and the two
     * `profile` claims the scenario turns on — `nickname` restricted to `billing`, `name` to nothing. The
     * storefront client names no audience, so it takes `default` from `templates.clients.default`; the
     * billing client names `billing`.
     */
    private fun twoAudienceConfig(
        billing: InteractiveFlowRegistry,
        storefront: InteractiveFlowRegistry,
    ): Map<String, Any> = passwordAuthConfig() and mapOf(
        "claims" to mapOf(
            "name" to mapOf("enabled" to true, "required" to true),
            "nickname" to mapOf("enabled" to true, "required" to true, "audience" to "billing"),
        ),
    ) and mapOf(
        "audiences" to mapOf(
            "billing" to mapOf("token-audience" to "https://billing.example.com"),
        ),
        "clients" to mapOf(
            billing.clientId() to clientConfig(billing) + mapOf("audience" to "billing"),
            storefront.clientId() to clientConfig(storefront),
        ),
    )

    private fun clientConfig(registry: InteractiveFlowRegistry): Map<String, Any> =
        publicClientConfig(registry, scopes = SCOPES.toList())

    private companion object {

        const val OWN_CLIENT_ID = "claim-restricted-storefront"
        const val OWN_FLOW_ID = "claim-restricted-storefront"

        const val BILLING_CLIENT_ID = "billing-app"
        const val BILLING_FLOW_ID = "billing"
        const val EMAIL = "claim-restricted@example.com"
        const val NAME = "Ada Lovelace"
        const val NICKNAME = "Ada"

        val SCOPES = arrayOf("openid", "profile")

        /** The value the billing sign-up collects for each claim its flow asks for, by claim id. */
        val COLLECTED: Map<String, String> = mapOf(
            "email" to EMAIL,
            "name" to NAME,
            "nickname" to NICKNAME,
        )
    }
}
