package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the issued `id_token` must carry the `nonce` from the authorization request,
 * unchanged.**
 *
 * Risk: the `nonce` binds an ID token to the specific authentication request a client initiated
 * (OpenID Connect Core §3.1.3.7 / §15.5.2). A client rejects an `id_token` whose `nonce` does not match
 * the one it sent, which defeats ID-token replay/injection. If the server dropped or altered the
 * `nonce`, that mitigation would silently break.
 *
 * This drives a sign-up with a known `nonce`, exchanges the code, and asserts the (signature-verified)
 * `id_token` echoes exactly that `nonce`, on each supported database.
 *
 * Source: [OpenID Connect Core 1.0 §3.1.3.7 (ID Token Validation)](https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation)
 * and [§15.5.2 (Nonce Implementation Notes)](https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes).
 */
@Tag("security")
class IdTokenCarriesNonceIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "id_token echoes the request nonce on {0}")
    @EnumSource(Database::class)
    fun idTokenEchoesRequestNonce(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val nonce = "integration-test-nonce-Xy7Zq"

            val tokens = registry.newFlow()
                .withNonce(nonce)
                .withSignUpHandler { mapOf("email" to "ada@example.com", "password" to "Str0ngP@ssw0rd!") }
                .run()
                .exchange()

            val idToken = checkNotNull(tokens.idToken()) { "the openid scope should yield an id_token" }
            val claims = verifyIdTokenSignature(sympauthy, idToken)

            assertEquals(
                nonce,
                claims.getStringClaim("nonce"),
                "the id_token must echo the request nonce unchanged (OIDC replay mitigation)",
            )
        }
    }
}
