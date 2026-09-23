package com.sympauthy.it.feature

import com.sympauthy.api.client.api.OpenidApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **an operator decides which OpenID channel carries each claim.**
 *
 * `claims.<id>.published-in` names the channels a claim's value travels through, and a claim naming none
 * travels through neither. Four custom claims are configured under the `profile` scope: `loyalty_tier`
 * names both channels, `preferences` names `userinfo`, `internal_ref` names `id-token` and `internal_note`
 * names none at all. Two OpenID claims sit beside them: `name`, which names nothing of its own and takes
 * both channels from the shipped `openid` template, and `nickname`, which overrides that template with an
 * empty list. One person signs up supplying every one of them, and the single authorization that follows is
 * read twice — the `id_token` it was issued, verified against the server's own key set, and the `/userinfo`
 * it reads with the access token issued beside it.
 *
 * Each channel must carry the claims that name it and the one that names both, and neither may carry the
 * claim that names the other or either claim that names nothing. That last pair is the point of the
 * default: a value no file placed is withheld rather than published, and `nickname` shows it holds for a
 * claim of the specification as much as for a deployment's own. `name` is the other half — the claims
 * OpenID Connect defines reach both channels because the shipped template says so, which is what a
 * deployment that never mentions publication keeps.
 *
 * No unit test reaches this: the two channels are built by different classes from the same collected rows,
 * and what proves they disagree on purpose is one grant answered by both. `/userinfo` carrying a custom
 * claim at all is new here — the response had no property for one — so those assertions read the
 * deserialized wire body, while `name` and `nickname` are read off their declared fields, which is what
 * shows the two living side by side.
 *
 * The second scenario reads the discovery document off the same configuration: `claims_supported` names
 * what a client could be told, so an enabled claim no channel carries must not appear there while `sub`,
 * `updated_at` and `name` — a generated claim in both channels, one in `/userinfo` alone, and one the
 * shipped template publishes — must. Nothing smaller proves it: the channels a generated claim reaches are
 * recorded in code, and what the document lists is read off the parsed configuration.
 *
 * Issue: [#485](https://github.com/sympauthy/sympauthy/issues/485), and
 * [`docs/security.md`](https://github.com/sympauthy/sympauthy/blob/main/docs/security.md).
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

    @ParameterizedTest(name = "the discovery document advertises only what a channel can supply on {0}")
    @EnumSource(Database::class)
    fun advertisesOnlyTheClaimsAChannelCanSupply(database: Database) {
        withContainer(database, extraConfig = claimsPublishedPerChannel(), scopes = SCOPES) { sympauthy, _ ->
            val supported = discovery(sympauthy).claimsSupported.orEmpty()

            assertTrue(supported.contains("sub"), "a generated claim both channels carry")
            assertTrue(supported.contains("updated_at"), "a generated claim /userinfo carries")
            assertTrue(supported.contains("name"), "an OpenID claim the shipped template publishes")
            assertFalse(supported.contains("nickname"), "an OpenID claim published in neither channel")
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
     * `name` is turned on and otherwise left alone: it keeps the `openid` template, the `profile` consent
     * scope and the channels the shipped file gives it, which is the point of asserting it.
     */
    private fun claimsPublishedPerChannel(): Map<String, Any> = mapOf(
        "claims" to mapOf(
            "name" to mapOf("enabled" to true, "required" to true),
            // Enabled and published nowhere: an OpenID claim the deployment withholds from both channels,
            // which the sign-up still collects and neither channel nor the discovery document may carry.
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

    private fun customClaim(publishedIn: List<String>? = null): Map<String, Any> = buildMap {
        put("enabled", true)
        put("required", true)
        put("type", "string")
        put(
            "acl",
            mapOf(
                "consent-scope" to "profile",
                "readable-by-user-when-consented" to "true",
                "writable-by-user-when-consented" to "true",
                "readable-by-client-when-consented" to "true",
            ),
        )
        publishedIn?.let { put("published-in", it) }
    }

    private companion object {

        const val EMAIL = "published-in@example.com"
        const val PASSWORD = "Str0ngP@ssw0rd!"
        const val FULL_NAME = "Ada Lovelace"
        const val GOLD = "gold"
        const val DARK = "dark"
        const val REFERENCE = "CRM-4417"
        const val NOTE = "renewal due"
        const val NICKNAME = "Ada"

        val SCOPES = listOf("openid", "profile")

        /** The value the sign-up collects for each claim its flow asks for, by claim id. */
        val COLLECTED: Map<String, String> = mapOf(
            "email" to EMAIL,
            "name" to FULL_NAME,
            "loyalty_tier" to GOLD,
            "preferences" to DARK,
            "internal_ref" to REFERENCE,
            "internal_note" to NOTE,
            "nickname" to NICKNAME,
        )
    }
}
