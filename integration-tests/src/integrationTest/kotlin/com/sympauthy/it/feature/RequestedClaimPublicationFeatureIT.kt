package com.sympauthy.it.feature

import com.nimbusds.jose.util.JSONObjectUtils
import com.sympauthy.api.client.api.OpenidApi
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.client.TokenResponse
import com.sympauthy.testcontainers.flow.FlowException
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Feature scenario — **a client asks for a claim in a channel the deployment opened to a request.**
 *
 * `claims.<id>.published-in-when-requested` names the channels a value travels through only for a request
 * that named the claim, and the `claims` parameter of OpenID Connect Core §5.5 is how a client names one.
 * Six custom claims sit under the `profile` scope: `loyalty_tier` is published in `userinfo` always and in
 * the id token on request, `shoe_size` is published in the id token always and in `userinfo` on request,
 * `audit_ref` is published nowhere and in the id token on request, `internal_note` opens no channel at all,
 * and `cross_tier` and `cross_ref` are published nowhere and open *both* channels to a request. One person
 * signs up supplying every one of them.
 *
 * The first scenario authorizes with a `claims` parameter naming `loyalty_tier`, `internal_note` and
 * `cross_tier` for the id token and `shoe_size` and `cross_ref` for `userinfo`, then reads the id token,
 * `/userinfo`, and the id token the refresh grant reissues. The id token must carry `loyalty_tier` because
 * the request asked and the deployment opened that channel, `/userinfo` must carry it too because it is
 * published there always, and `shoe_size` must appear in both for the mirror-image reason. `internal_note`
 * must appear in neither: a request cannot reach a channel nobody opened, and the response is a normal one
 * rather than an error. `audit_ref` must appear nowhere either, because this request did not name it —
 * which is what separates the second list from publishing outright.
 *
 * **`cross_tier` and `cross_ref` are what tell the two members of the parameter apart.** Both open both
 * channels, so nothing in the configuration decides which channel carries which; the request alone does,
 * and it named one in each member. `cross_tier` must therefore reach the id token and not `/userinfo`, and
 * `cross_ref` the other way round. Every other claim here is published always in whichever channel it is
 * not asked for in, so a server that read the parameter as one set of names rather than one per channel
 * would answer all of them correctly and these two wrongly.
 *
 * The refresh is the half no unit test reaches and the one the design turns on: the parameter arrived at
 * `/authorize`, the session that received it is gone, and the id token the refresh mints has to carry the
 * same claims as the one the code exchange did. A request held on the session alone would pass every other
 * assertion here and fail this one.
 *
 * The second scenario authorizes without any `claims` parameter and asserts the same deployment answers
 * exactly what `published-in` alone says — every on-request channel stays shut, the two that are open in
 * both directions included. That is what makes the key
 * safe to ship: a deployment that opened a channel is unchanged for every client that does not ask.
 *
 * The third scenario sends `claims=not-json`. §5.5 fixes the value as a JSON object, so the authorization
 * is refused rather than answered: it is collected with the other unreadable parameters of that endpoint,
 * and the `303` sends the person to the flow's error page instead of to a step, which is what the driver
 * refuses to walk.
 *
 * The parameter reaches `/authorize` through `InteractiveFlow.withAuthorizationParam`, so the driver still
 * owns the request — the PKCE pair, the state and the code exchange are its own.
 *
 * Issue: [#493](https://github.com/sympauthy/sympauthy/issues/493), and `docs/design/claims.md`.
 */
@Tag("feature")
class RequestedClaimPublicationFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a requested claim reaches the channel the deployment opened on {0}")
    @EnumSource(Database::class)
    fun carriesARequestedClaimInTheChannelTheDeploymentOpened(database: Database) {
        withRequestableClaims(database) { sympauthy, registry ->
            val tokens = signUpAsking(registry, REQUEST)

            val idTokenClaims = verifyIdTokenSignature(sympauthy, requireIdToken(tokens.idToken()))
            assertEquals(GOLD, idTokenClaims.getStringClaim("loyalty_tier"), "asked for, and the channel is open")
            assertEquals(SIZE, idTokenClaims.getStringClaim("shoe_size"), "published here for every request")
            assertNull(idTokenClaims.getClaim("internal_note"), "asked for, and no channel was opened for it")
            assertNull(idTokenClaims.getClaim("audit_ref"), "the channel is open and this request did not ask")
            assertEquals(
                CROSS_TIER,
                idTokenClaims.getStringClaim("cross_tier"),
                "both channels are open and the id_token member is the one that named it",
            )
            assertNull(
                idTokenClaims.getClaim("cross_ref"),
                "both channels are open and the userinfo member alone named it, so this one must not carry it",
            )

            val userInfo = withApiClient(sympauthy, token = tokens.accessToken()) { ctx ->
                ctx.getBean(OpenidApi::class.java).getUserInfo().block()
            }
            requireNotNull(userInfo) { "userinfo returned an empty body" }
            assertEquals(GOLD, userInfo["loyalty_tier"], "published here for every request")
            assertEquals(SIZE, userInfo["shoe_size"], "asked for, and the channel is open")
            assertNull(userInfo["internal_note"], "asked for, and no channel was opened for it")
            assertNull(userInfo["audit_ref"], "published in no channel at all")
            assertEquals(
                CROSS_REF,
                userInfo["cross_ref"],
                "both channels are open and the userinfo member is the one that named it",
            )
            assertNull(
                userInfo["cross_tier"],
                "both channels are open and the id_token member alone named it, so this one must not carry it",
            )

            val refreshToken = requireNotNull(tokens.refreshToken()) {
                "refresh-enabled should yield a refresh token"
            }
            val refreshed = refresh(sympauthy, registry, refreshToken)
            val refreshedIdToken = requireNotNull(JSONObjectUtils.getString(refreshed, "id_token")) {
                "the refresh response should carry an id_token, body=$refreshed"
            }
            val refreshedClaims = verifyIdTokenSignature(sympauthy, refreshedIdToken)
            assertNotEquals(tokens.idToken(), refreshedIdToken, "the refresh should issue a new id_token")
            assertEquals(
                GOLD,
                refreshedClaims.getStringClaim("loyalty_tier"),
                "the request outlives the session that carried it, so the reissued id_token answers it too",
            )
            assertEquals(
                CROSS_TIER,
                refreshedClaims.getStringClaim("cross_tier"),
                "the reissued id_token keeps the channel split the request asked for",
            )
            assertNull(refreshedClaims.getClaim("cross_ref"), "and keeps the other member out of this channel")
            assertNull(refreshedClaims.getClaim("audit_ref"), "the reissued id_token asks for no more than the first")
        }
    }

    @ParameterizedTest(name = "an on-request channel stays shut for a client that asks for nothing on {0}")
    @EnumSource(Database::class)
    fun leavesAnOnRequestChannelShutWhenNothingIsAsked(database: Database) {
        withRequestableClaims(database) { sympauthy, registry ->
            val tokens = signUpAsking(registry, claims = null)

            val idTokenClaims = verifyIdTokenSignature(sympauthy, requireIdToken(tokens.idToken()))
            assertEquals(SIZE, idTokenClaims.getStringClaim("shoe_size"), "published here for every request")
            assertNull(idTokenClaims.getClaim("loyalty_tier"), "the channel is open and nothing asked")
            assertNull(idTokenClaims.getClaim("audit_ref"), "the channel is open and nothing asked")
            assertNull(idTokenClaims.getClaim("cross_tier"), "both channels are open and nothing asked")

            val userInfo = withApiClient(sympauthy, token = tokens.accessToken()) { ctx ->
                ctx.getBean(OpenidApi::class.java).getUserInfo().block()
            }
            requireNotNull(userInfo) { "userinfo returned an empty body" }
            assertEquals(GOLD, userInfo["loyalty_tier"], "published here for every request")
            assertNull(userInfo["shoe_size"], "the channel is open and nothing asked")
            assertNull(userInfo["cross_ref"], "both channels are open and nothing asked")
        }
    }

    @ParameterizedTest(name = "a claims parameter that is not a JSON object fails the authorization on {0}")
    @EnumSource(Database::class)
    fun refusesAClaimsParameterThatIsNotAJsonObject(database: Database) {
        withRequestableClaims(database) { _, registry ->
            val flow = registry.newFlow()
                .withAuthorizationParam("claims", "not-json")
                .withSignUpHandler { credentials(EMAIL) }

            assertThrows<FlowException>("a claims parameter this server cannot read yields no code") { flow.run() }
        }
    }

    /**
     * Runs [block] against a container whose custom claims open the channels these scenarios turn on,
     * for a confidential client, so that the refresh grant can be presented with the client's own secret.
     */
    private fun withRequestableClaims(
        database: Database,
        block: (SympauthyContainer, InteractiveFlowRegistry) -> Unit,
    ) = withContainer(
        database,
        extraConfig = claimsOpenedToARequest(),
        client = Client.confidentialClient(clientId, CLIENT_SECRET),
        scopes = SCOPES,
        block = block,
    )

    /**
     * Signs a person up through an authorization asking for [claims], and exchanges the code it yields.
     * A null [claims] sends no such parameter at all.
     */
    private fun signUpAsking(registry: InteractiveFlowRegistry, claims: String?): TokenResponse =
        registry.newFlow()
            .apply { claims?.let { withAuthorizationParam("claims", it) } }
            .withSignUpHandler { credentials(EMAIL) }
            .withClaimsHandler { requested -> requested.associate { it.id() to COLLECTED.getValue(it.id()) } }
            .run()
            .exchange()

    /**
     * The six custom claims the scenarios turn on, under the `profile` scope the person consents to, and
     * the client allowed and defaulted to that scope — the base configuration allows `openid` alone, and a
     * flow asking for more than its client allows is refused before any step runs.
     */
    private fun claimsOpenedToARequest(): Map<String, Any> = mapOf(
        "auth" to mapOf("token" to mapOf("refresh-enabled" to true)),
        "claims" to mapOf(
            "loyalty_tier" to customClaim(
                publishedIn = listOf("userinfo"),
                publishedInWhenRequested = listOf("id-token"),
            ),
            "shoe_size" to customClaim(
                publishedIn = listOf("id-token"),
                publishedInWhenRequested = listOf("userinfo"),
            ),
            // Carried nowhere until a request names it, which the scenarios' requests do not: it is what
            // separates a channel opened to a request from one published in outright.
            "audit_ref" to customClaim(publishedInWhenRequested = listOf("id-token")),
            "internal_note" to customClaim(),
            // Both channels open to a request and neither published in, so the one the request names is
            // the only one that may carry the value. They are the pair that tells the two members of the
            // parameter apart: a request read as one set would put each of them in both channels.
            "cross_tier" to customClaim(publishedInWhenRequested = listOf("id-token", "userinfo")),
            "cross_ref" to customClaim(publishedInWhenRequested = listOf("id-token", "userinfo")),
        ),
        "clients" to mapOf(
            clientId to mapOf("allowed-scopes" to SCOPES, "default-scopes" to SCOPES),
        ),
    )

    private fun customClaim(
        publishedIn: List<String> = emptyList(),
        publishedInWhenRequested: List<String> = emptyList(),
    ): Map<String, Any> = mapOf(
        "enabled" to true,
        "required" to true,
        "type" to "string",
        // The `personal` template, because the flow collects the value: it is what says the claim belongs
        // to the person, and it carries the consent flags and the client read these scenarios need.
        "template" to "personal",
        // Always written, so each claim is published in exactly the places a scenario names: the template
        // offers the two OpenID channels and the document, and an empty list withholds all three.
        "published-in" to publishedIn,
        "published-in-when-requested" to publishedInWhenRequested,
        "acl" to mapOf(
            "consent-scope" to "profile",
            "readable-by-client-when-consented" to "true",
        ),
    )

    private companion object {

        const val EMAIL = "published-in-when-requested@example.com"
        const val CLIENT_SECRET = "requested-claim-publication-client-secret"
        const val GOLD = "gold"
        const val SIZE = "43"
        const val REFERENCE = "CRM-9120"
        const val NOTE = "renewal due"
        const val CROSS_TIER = "platinum"
        const val CROSS_REF = "CRM-7781"

        val SCOPES = listOf("openid", "profile")

        /**
         * Three claims for the id token and two for `/userinfo`, as §5.5 spells the parameter. The
         * `essential` member against `internal_note` is read past, which is the one assertion that would
         * change if the flag ever came to mean something.
         */
        const val REQUEST = """{"id_token":{"loyalty_tier":null,"internal_note":{"essential":true},""" +
            """"cross_tier":null},"userinfo":{"shoe_size":null,"cross_ref":null}}"""

        /** The value the sign-up collects for each claim its flow asks for, by claim id. */
        val COLLECTED: Map<String, String> = mapOf(
            "email" to EMAIL,
            "loyalty_tier" to GOLD,
            "shoe_size" to SIZE,
            "audit_ref" to REFERENCE,
            "internal_note" to NOTE,
            "cross_tier" to CROSS_TIER,
            "cross_ref" to CROSS_REF,
        )
    }
}
