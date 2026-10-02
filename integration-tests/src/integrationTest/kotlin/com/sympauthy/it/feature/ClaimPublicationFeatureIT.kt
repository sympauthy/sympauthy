package com.sympauthy.it.feature

import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.api.client.api.OpenidApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **an operator decides every place each claim is published in.**
 *
 * `claims.<id>.published-in` names them, and a claim naming none is published nowhere. The first scenario
 * covers the two OpenID channels: four custom claims sit under the `profile` scope — `loyalty_tier` names
 * both, `preferences` names `userinfo`, `internal_ref` names `id-token` and `internal_note` names nothing —
 * beside two OpenID claims, `name`, which takes both channels from the shipped `personal` template, and
 * `nickname`, which overrides that template with an empty list. One person signs up supplying every one of
 * them, and the single authorization that follows is read twice: the `id_token` it was issued, verified
 * against the server's own key set, and the `/userinfo` it reads with the access token issued beside it.
 *
 * Each channel must carry the claims that name it and the one that names both, and neither may carry the
 * claim that names the other or either claim that names nothing. That last pair is the point of the
 * default: a value no file placed is withheld rather than published, and `nickname` shows it holds for a
 * claim of the specification as much as for a deployment's own. `name` is the other half — the claims
 * OpenID Connect defines reach both channels because the shipped template says so, which is what a
 * deployment that never mentions publication keeps.
 *
 * No unit test reaches this: the places are built by different classes from the same collected rows, and
 * what proves they disagree on purpose is one grant answered by all of them. `/userinfo` carrying a custom
 * claim at all is new here — the response had no property for one — so those assertions read the
 * deserialized wire body, while `name` and `nickname` are read off their declared fields, which is what
 * shows the two living side by side.
 *
 * The second scenario reads the two places a resource server holding a token reaches, off a confidential
 * client's own grant: `stored_tier` names `access-token`, `stored_ref` names `introspection`, and
 * `secret_note` names both while the ACL refuses the client every one of its claims. The decoded access
 * token and the introspection response must each carry the claim that named it and not the other's, the
 * id token issued beside them must carry neither, and none of the three may carry `secret_note` — naming a
 * place is not being allowed to reach it, and the ACL is what decides who may be told. Two components of
 * the address group name `introspection` beside them, because the one object OpenID Connect Core §5.1.1
 * defines is what every place assembles them into and the response serializes it as a nested member rather
 * than as a claim of its own.
 *
 * `secret_note` is required as well as hidden, so the scenario reaches the token exchange only if the flow
 * reads a person's claims back through the half it collects them through. It takes the whole instance
 * because the three places are answered by two generators and a controller from one authorization, and
 * because introspection is answered to a client that authenticated.
 *
 * Issue: [#485](https://github.com/sympauthy/sympauthy/issues/485),
 * [#510](https://github.com/sympauthy/sympauthy/issues/510) and
 * [#512](https://github.com/sympauthy/sympauthy/issues/512), and `docs/design/claims.md`.
 */
@Tag("feature")
class ClaimPublicationFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "each channel carries the claims that name it on {0}")
    @EnumSource(Database::class)
    fun publishesEachClaimInTheChannelsItNames(database: Database) {
        withContainer(database, extraConfig = claimsPublishedPerChannel(), scopes = SCOPES) { sympauthy, registry ->
            val tokens = signUp(registry)

            val idTokenClaims = verifyIdTokenSignature(
                sympauthy,
                requireNotNull(tokens.idToken()) { "the openid scope should yield an id_token" },
            )
            assertEquals(FULL_NAME, idTokenClaims.getStringClaim("name"), "an OpenID claim, from the template")
            assertEquals(GOLD, idTokenClaims.getStringClaim("loyalty_tier"), "a claim naming both channels")
            assertEquals(REFERENCE, idTokenClaims.getStringClaim("internal_ref"), "a claim naming the id token")
            assertNull(idTokenClaims.getClaim("preferences"), "a claim naming /userinfo alone")
            assertNull(idTokenClaims.getClaim("internal_note"), "a claim naming no channel at all")
            assertNull(idTokenClaims.getClaim("nickname"), "an OpenID claim the deployment publishes nowhere")

            val userInfo = withApiClient(sympauthy, token = tokens.accessToken()) { ctx ->
                ctx.getBean(OpenidApi::class.java).getUserInfo().block()
            }
            requireNotNull(userInfo) { "userinfo returned an empty body" }

            assertEquals(FULL_NAME, userInfo.name, "an OpenID claim, under the property this resource declares")
            assertEquals(GOLD, userInfo["loyalty_tier"], "a claim naming both channels")
            assertEquals(DARK, userInfo["preferences"], "a claim naming /userinfo")
            assertNull(userInfo["internal_ref"], "a claim naming the id token alone")
            assertNull(userInfo["internal_note"], "a claim naming no channel at all")
            assertNull(userInfo.nickname, "an OpenID claim the deployment publishes nowhere")
        }
    }

    @ParameterizedTest(name = "a token and its introspection each carry the claims that name them on {0}")
    @EnumSource(Database::class)
    fun publishesEachClaimInTheTokenPlaceItNames(database: Database) {
        val confidentialClient = Client.confidentialClient(clientId, CLIENT_SECRET)

        withContainer(
            database,
            extraConfig = claimsPublishedPerTokenPlace(),
            client = confidentialClient,
            scopes = TOKEN_PLACE_SCOPES,
        ) { sympauthy, registry ->
            val tokens = signUp(registry)
            val accessToken = requireNotNull(tokens.accessToken()) { "the exchange should yield an access token" }

            val accessTokenClaims = SignedJWT.parse(accessToken).jwtClaimsSet
            assertEquals(GOLD, accessTokenClaims.getStringClaim("stored_tier"), "a claim naming the access token")
            assertNull(accessTokenClaims.getClaim("stored_ref"), "a claim naming introspection alone")
            assertNull(accessTokenClaims.getClaim("secret_note"), "a claim the ACL refuses the client")

            val idTokenClaims = verifyIdTokenSignature(
                sympauthy,
                requireNotNull(tokens.idToken()) { "the openid scope should yield an id_token" },
            )
            assertNull(idTokenClaims.getClaim("stored_tier"), "a claim naming the access token alone")
            assertNull(idTokenClaims.getClaim("stored_ref"), "a claim naming introspection alone")

            val auth = mapOf("Authorization" to basicAuth(registry.clientId(), checkNotNull(registry.clientSecret())))
            val response = httpPostForm(
                discovery(sympauthy).introspectionEndpoint!!,
                mapOf("token" to accessToken),
                auth,
            )
            assertEquals(200, response.statusCode(), "introspection should answer the client, body=${response.body()}")
            val introspection = JSONObjectUtils.parse(response.body())

            assertEquals(true, introspection["active"])
            assertEquals(REFERENCE, introspection["stored_ref"], "a claim naming introspection")
            assertNull(introspection["stored_tier"], "a claim naming the access token alone")
            assertNull(introspection["secret_note"], "a claim the ACL refuses the client")

            @Suppress("UNCHECKED_CAST")
            val address = introspection["address"] as? Map<String, Any>
            requireNotNull(address) { "the claims of the address group should be one object" }
            assertEquals(LOCALITY, address["locality"], "a component of the address object")
            assertEquals("$LOCALITY\n$COUNTRY", address["formatted"], "the rendered address")
        }
    }

    private fun signUp(registry: InteractiveFlowRegistry) = registry.newFlow()
        .withSignUpHandler { mapOf("email" to EMAIL, "password" to PASSWORD) }
        .withClaimsHandler { requested -> requested.associate { it.id() to COLLECTED.getValue(it.id()) } }
        .run()
        .exchange()

    /**
     * The four custom claims the scenario turns on, under the `profile` scope the person consents to, and
     * the client allowed and defaulted to that scope — the base configuration allows `openid` alone, and a
     * flow asking for more than its client allows is refused before any step runs.
     *
     * `name` is turned on and otherwise left alone: it keeps the `personal` template, the `profile` consent
     * scope and the channels the shipped file gives it, which is the point of asserting it.
     */
    private fun claimsPublishedPerChannel(): Map<String, Any> = mapOf(
        "claims" to mapOf(
            "name" to mapOf("enabled" to true, "required" to true),
            // Enabled and published nowhere: an OpenID claim the deployment withholds from both channels,
            // which the sign-up still collects and neither channel may carry.
            "nickname" to mapOf("enabled" to true, "published-in" to emptyList<String>()),
            "loyalty_tier" to customClaim(publishedIn = listOf("id-token", "userinfo")),
            "preferences" to customClaim(publishedIn = listOf("userinfo")),
            "internal_ref" to customClaim(publishedIn = listOf("id-token")),
            "internal_note" to customClaim(),
        ),
        "clients" to mapOf(
            clientId to mapOf("allowed-scopes" to SCOPES, "default-scopes" to SCOPES),
        ),
    )

    private fun customClaim(
        publishedIn: List<String> = emptyList(),
        readableByClient: Boolean = true,
    ): Map<String, Any> = buildMap {
        put("enabled", true)
        put("required", true)
        put("type", "string")
        // The `personal` template, because the flow collects the value: it is what says the claim belongs
        // to the person, and it carries the consent flags and the client read this scenario needs.
        put("template", "personal")
        // Always written, so each claim is published in exactly the places the scenario names: the
        // template offers the two OpenID channels and the document, and an empty list withholds all three.
        put("published-in", publishedIn)
        put(
            "acl",
            mapOf(
                "consent-scope" to "profile",
                "readable-by-client-when-consented" to readableByClient.toString(),
            ),
        )
    }

    /**
     * The three custom claims the second scenario turns on: one in the access token, one in the
     * introspection response, and one naming both that the ACL refuses the client. `secret_note` is still
     * required, collected in the flow and readable by the person, so the sign-up supplies a value for it and
     * what keeps it out of both places is the client's half of the ACL alone.
     */
    private fun claimsPublishedPerTokenPlace(): Map<String, Any> = mapOf(
        "claims" to mapOf(
            "stored_tier" to customClaim(publishedIn = listOf("access-token")),
            "stored_ref" to customClaim(publishedIn = listOf("introspection")),
            // Two components of the address group, which every place assembles into the one object
            // OpenID Connect Core §5.1.1 defines rather than publishing as members of their own.
            "locality" to addressClaim(),
            "country" to addressClaim(),
            "secret_note" to customClaim(
                publishedIn = listOf("access-token", "introspection"),
                readableByClient = false,
            ),
        ),
        "clients" to mapOf(
            clientId to mapOf(
                "allowed-scopes" to TOKEN_PLACE_SCOPES,
                "default-scopes" to TOKEN_PLACE_SCOPES,
            ),
        ),
    )

    /**
     * A component of the address group, turned on and published in the introspection response, keeping the
     * `personal` template's consent scope and group.
     */
    private fun addressClaim(): Map<String, Any> = mapOf(
        "enabled" to true,
        "published-in" to listOf("introspection"),
    )

    private companion object {

        const val EMAIL = "published-in@example.com"
        const val PASSWORD = "Str0ngP@ssw0rd!"
        const val FULL_NAME = "Ada Lovelace"
        const val GOLD = "gold"
        const val DARK = "dark"
        const val REFERENCE = "CRM-4417"
        const val NOTE = "renewal due"
        const val NICKNAME = "Ada"
        const val LOCALITY = "Brussels"
        const val COUNTRY = "Belgium"

        val SCOPES = listOf("openid", "profile")

        /** The second scenario consents to `address` as well, for the claims of that group. */
        val TOKEN_PLACE_SCOPES = SCOPES + "address"

        const val CLIENT_SECRET = "claim-publication-client-secret-value"

        /** The value the sign-up collects for each claim its flow asks for, by claim id. */
        val COLLECTED: Map<String, String> = mapOf(
            "email" to EMAIL,
            "name" to FULL_NAME,
            "loyalty_tier" to GOLD,
            "preferences" to DARK,
            "internal_ref" to REFERENCE,
            "internal_note" to NOTE,
            "nickname" to NICKNAME,
            "stored_tier" to GOLD,
            "stored_ref" to REFERENCE,
            "secret_note" to NOTE,
            "locality" to LOCALITY,
            "country" to COUNTRY,
        )
    }
}
