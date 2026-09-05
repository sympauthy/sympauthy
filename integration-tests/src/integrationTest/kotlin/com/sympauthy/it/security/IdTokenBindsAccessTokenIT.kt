package com.sympauthy.it.security

import com.nimbusds.jose.util.Base64URL
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.charset.StandardCharsets.US_ASCII
import java.security.MessageDigest

/**
 * Security scenario — **the issued `id_token` must name the access token issued in the same response,
 * as its `at_hash` claim.**
 *
 * Risk: `at_hash` is what lets a client detect an access token substituted for the one the
 * authorization server issued alongside the identity it just verified (OpenID Connect Core §3.1.3.6).
 * This server already refuses a provider's `id_token` whose `at_hash` does not match, so a claim it
 * omitted or computed over the wrong string would hold third parties to a rule it did not keep.
 *
 * This drives a sign-up, exchanges the code, recomputes the hash from the `access_token` the client
 * actually received, and asserts the (signature-verified) `id_token` carries exactly it, on each
 * supported database.
 *
 * Source: [OpenID Connect Core 1.0 §3.1.3.6 (ID Token)](https://openid.net/specs/openid-connect-core-1_0.html#CodeIDToken).
 */
@Tag("security")
class IdTokenBindsAccessTokenIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "id_token at_hash names the access token issued with it on {0}")
    @EnumSource(Database::class)
    fun idTokenAtHashNamesTheAccessToken(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val tokens = registry.newFlow()
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }
                .run()
                .exchange()

            val idToken = checkNotNull(tokens.idToken()) { "the openid scope should yield an id_token" }
            val claims = verifyIdTokenSignature(sympauthy, idToken)

            val digest = MessageDigest.getInstance("SHA-256").digest(tokens.accessToken().toByteArray(US_ASCII))
            assertEquals(
                Base64URL.encode(digest.copyOf(digest.size / 2)).toString(),
                claims.getStringClaim("at_hash"),
                "the at_hash must be the left half of the access token's hash (OIDC token-substitution mitigation)",
            )
        }
    }
}
