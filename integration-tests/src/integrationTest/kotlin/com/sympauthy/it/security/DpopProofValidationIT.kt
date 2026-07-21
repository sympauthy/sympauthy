package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

/**
 * Security scenario — **the token endpoint must reject a DPoP proof that is well-formed and correctly
 * signed but does not bind to this request, with `invalid_dpop_proof` (HTTP 400).**
 *
 * Risk: DPoP (RFC 9449 §4.3) only prevents proof replay and token misuse if the server checks that each
 * proof is bound to *this* HTTP method (`htm`), *this* URL (`htu`), and is fresh (`iat`). A proof that
 * was captured for another endpoint, another method, or minted long ago must not be accepted — else an
 * attacker could replay a captured proof. Each check is validated independently here.
 *
 * All three proofs are otherwise valid (ES256-signed, `typ=dpop+jwt`, embedded public JWK); each has
 * exactly one binding defect — wrong `htu`, wrong `htm`, or a stale `iat` outside the 60s window — and
 * must be rejected with `invalid_dpop_proof`, on each supported database.
 *
 * Source: [RFC 9449 §4.3 (Checking DPoP Proofs)](https://datatracker.ietf.org/doc/html/rfc9449#section-4.3).
 */
@Tag("security")
class DpopProofValidationIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "token endpoint rejects DPoP proofs not bound to the request on {0}")
    @EnumSource(Database::class)
    fun tokenEndpointRejectsInvalidDpopProofs(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val tokenEndpoint = discovery(sympauthy)["token_endpoint"] as String
            // The DPoP proof is validated before the grant, so the rest of the request is immaterial.
            val baseForm = mapOf(
                "grant_type" to "authorization_code",
                "code" to "placeholder-code",
                "redirect_uri" to registry.redirectUri(),
                "client_id" to registry.clientId(),
                "code_verifier" to generatePkce().verifier,
            )

            fun assertRejected(label: String, proof: String) {
                val response = httpPostForm(tokenEndpoint, baseForm, dpopHeader(proof))
                assertEquals(400, response.statusCode(), "$label must be rejected, body=${response.body()}")
                assertTrue(
                    response.body().contains("invalid_dpop_proof"),
                    "$label: expected invalid_dpop_proof, was: ${response.body()}",
                )
            }

            assertRejected("wrong htu", dpopProof(htu = "https://attacker.example/api/oauth2/token"))
            assertRejected("wrong htm", dpopProof(htu = tokenEndpoint, htm = "GET"))
            assertRejected("stale iat", dpopProof(htu = tokenEndpoint, iat = Instant.now().minusSeconds(600)))
        }
    }
}
