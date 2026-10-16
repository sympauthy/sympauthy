package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.config.model.EnabledUrlsConfig
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class DpopManagerTest {

    private val urlsConfig = EnabledUrlsConfig(root = URI.create("https://auth.example.com"))
    private val dpopManager = DpopManager(urlsConfig)

    private lateinit var ecKey: ECKey
    private lateinit var signer: ECDSASigner

    @BeforeEach
    fun setUp() {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        ecKey = ECKey.Builder(Curve.P_256, keyPair.public as ECPublicKey)
            .privateKey(keyPair.private as ECPrivateKey)
            .keyUse(KeyUse.SIGNATURE)
            .build()
        signer = ECDSASigner(ecKey)
    }

    private fun boundRequest(
        method: String = "POST",
        uri: URI = URI.create("/api/oauth2/token"),
        accessToken: String? = null
    ) = DpopBoundRequest(method = method, uri = uri, accessToken = accessToken)

    private fun userInfoRequest() = boundRequest(
        method = "GET",
        uri = URI.create("/api/openid/userinfo"),
        accessToken = ACCESS_TOKEN
    )

    private fun createUserInfoProof(
        ecKeyOverride: ECKey = ecKey,
        signerOverride: ECDSASigner = signer
    ) = createValidDpopProof(
        htm = "GET",
        htu = "https://auth.example.com/api/openid/userinfo",
        ath = ACCESS_TOKEN_HASH,
        ecKeyOverride = ecKeyOverride,
        signerOverride = signerOverride
    )

    private fun tokenBoundTo(jkt: String?) = mockk<AuthenticationToken> {
        every { dpopJkt } returns jkt
    }

    private fun createOtherEcKey(): ECKey {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()
        return ECKey.Builder(Curve.P_256, keyPair.public as ECPublicKey)
            .privateKey(keyPair.private as ECPrivateKey)
            .keyUse(KeyUse.SIGNATURE)
            .build()
    }

    private fun createValidDpopProof(
        htm: String = "POST",
        htu: String = "https://auth.example.com/api/oauth2/token",
        iat: Instant = Instant.now(),
        jti: String = UUID.randomUUID().toString(),
        typ: String = "dpop+jwt",
        ath: String? = null,
        ecKeyOverride: ECKey = ecKey,
        signerOverride: ECDSASigner = signer
    ): String {
        val publicJwk = ecKeyOverride.toPublicJWK()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType(typ))
            .jwk(publicJwk)
            .build()
        val claims = JWTClaimsSet.Builder()
            .jwtID(jti)
            .claim("htm", htm)
            .claim("htu", htu)
            .issueTime(Date.from(iat))
            .apply { ath?.let { claim("ath", it) } }
            .build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(signerOverride)
        return signedJwt.serialize()
    }

    @Test
    fun `validateDpopProof - Returns null when no DPoP header`() = runTest {
        assertNull(dpopManager.validateDpopProof(emptyList(), boundRequest()))
    }

    @Test
    fun `validateDpopProof - Throws when multiple DPoP headers`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateDpopProof(listOf("proof1", "proof2"), boundRequest())
        }
        assertEquals(INVALID_DPOP_PROOF, exception.errorCode)
        assertEquals("dpop.multiple_headers", exception.detailsId)
    }

    @Test
    fun `validateDpopProof - Returns DpopProof on valid proof`() = runTest {
        val proof = createValidDpopProof()
        val result = dpopManager.validateDpopProof(listOf(proof), boundRequest())

        assertNotNull(result)
        assertNotNull(result!!.jkt)
        assertTrue(result.jkt.isNotBlank())
    }

    private fun createDpopProofWithClaims(claims: JWTClaimsSet): String {
        val publicJwk = ecKey.toPublicJWK()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(publicJwk)
            .build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(signer)
        return signedJwt.serialize()
    }

    @Test
    fun `validateProof - Throws on malformed JWT`() = runTest {
        val request = boundRequest()
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof("not-a-jwt", request)
        }
        assertEquals("dpop.malformed", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when typ is not dpop+jwt`() = runTest {
        val proof = createValidDpopProof(typ = "JWT")
        val request = boundRequest()
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.invalid_type", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws on unsupported algorithm`() = runTest {
        val publicJwk = ecKey.toPublicJWK()
        val header = JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(publicJwk)
            .build()
        val claims = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .claim("htm", "POST")
            .claim("htu", "https://auth.example.com/api/oauth2/token")
            .issueTime(Date())
            .build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(com.nimbusds.jose.crypto.MACSigner("a-very-long-secret-key-that-is-at-least-32-bytes-long!"))
        val proof = signedJwt.serialize()
        val request = boundRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.unsupported_algorithm", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when jti is missing`() = runTest {
        val proof = createDpopProofWithClaims(
            JWTClaimsSet.Builder()
                .claim("htm", "POST")
                .claim("htu", "https://auth.example.com/api/oauth2/token")
                .issueTime(Date())
                .build()
        )
        val request = boundRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.missing_claims", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when htm is missing`() = runTest {
        val proof = createDpopProofWithClaims(
            JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htu", "https://auth.example.com/api/oauth2/token")
                .issueTime(Date())
                .build()
        )
        val request = boundRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.missing_claims", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when htu is missing`() = runTest {
        val proof = createDpopProofWithClaims(
            JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", "POST")
                .issueTime(Date())
                .build()
        )
        val request = boundRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.missing_claims", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when iat is missing`() = runTest {
        val proof = createDpopProofWithClaims(
            JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", "POST")
                .claim("htu", "https://auth.example.com/api/oauth2/token")
                .build()
        )
        val request = boundRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.missing_claims", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when htm does not match request method`() = runTest {
        val proof = createValidDpopProof(htm = "GET")
        val request = boundRequest(method = "POST")

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.invalid_htm", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when htu does not match request URI`() = runTest {
        val proof = createValidDpopProof(htu = "https://other.example.com/api/oauth2/token")
        val request = boundRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.invalid_htu", exception.detailsId)
    }

    @Test
    fun `validateProof - Strips query string from request URI for htu comparison`() = runTest {
        val proof = createValidDpopProof(htu = "https://auth.example.com/api/oauth2/token")
        val request = boundRequest(uri = URI.create("/api/oauth2/token?foo=bar"))

        val result = dpopManager.validateProof(proof, request)
        assertNotNull(result)
    }

    @Test
    fun `validateProof - Throws when iat is too old`() = runTest {
        val proof = createValidDpopProof(iat = Instant.now().minusSeconds(120))
        val request = boundRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.expired", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when iat is too far in the future`() = runTest {
        val proof = createValidDpopProof(iat = Instant.now().plusSeconds(120))
        val request = boundRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.expired", exception.detailsId)
    }

    @Test
    fun `validateProof - Accepts iat within acceptable window`() = runTest {
        val proof = createValidDpopProof(iat = Instant.now().minusSeconds(30))
        val request = boundRequest()

        val result = dpopManager.validateProof(proof, request)
        assertNotNull(result)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `validateProof - Throws when signature is invalid`() = runTest {
        val proof = createValidDpopProof()
        val parts = proof.split(".")
        val tamperedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"jti":"tampered","htm":"POST","htu":"https://auth.example.com/api/oauth2/token","iat":${Instant.now().epochSecond}}""".toByteArray())
        val tamperedProof = "${parts[0]}.$tamperedPayload.${parts[2]}"

        val request = boundRequest()
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(tamperedProof, request)
        }
        assertEquals("dpop.invalid_signature", exception.detailsId)
    }

    @Test
    fun `validateProof - Returns consistent jkt for the same key`() = runTest {
        val proof1 = createValidDpopProof(jti = UUID.randomUUID().toString())
        val proof2 = createValidDpopProof(jti = UUID.randomUUID().toString())
        val request = boundRequest()

        val result1 = dpopManager.validateProof(proof1, request)
        val result2 = dpopManager.validateProof(proof2, request)

        assertEquals(result1.jkt, result2.jkt)
    }

    @Test
    fun `validateProof - Returns different jkt for different keys`() = runTest {
        val otherEcKey = createOtherEcKey()
        val otherSigner = ECDSASigner(otherEcKey)

        val proof1 = createValidDpopProof()
        val proof2 = createValidDpopProof(ecKeyOverride = otherEcKey, signerOverride = otherSigner)
        val request = boundRequest()

        val result1 = dpopManager.validateProof(proof1, request)
        val result2 = dpopManager.validateProof(proof2, request)

        assertNotEquals(result1.jkt, result2.jkt)
    }

    @Test
    fun `getRequestUri - Strips query from request path`() = runTest {
        val request = boundRequest(uri = URI.create("/api/oauth2/token?code=abc&state=xyz"))
        assertEquals("https://auth.example.com/api/oauth2/token", dpopManager.getRequestUri(request))
    }

    @Test
    fun `getRequestUri - Uses urls root for scheme and authority`() = runTest {
        val request = boundRequest(uri = URI.create("/api/oauth2/token"))
        assertEquals("https://auth.example.com/api/oauth2/token", dpopManager.getRequestUri(request))
    }

    @Test
    fun `validateProof - Accepts ath carrying the hash of the access token presented`() = runTest {
        val result = dpopManager.validateProof(createUserInfoProof(), userInfoRequest())
        assertEquals(ecKey.computeThumbprint().toString(), result.jkt)
    }

    @Test
    fun `validateProof - Throws when ath is missing beside an access token`() = runTest {
        val proof = createValidDpopProof(htm = "GET", htu = "https://auth.example.com/api/openid/userinfo")

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, userInfoRequest())
        }
        assertEquals("dpop.invalid_ath", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when ath is the hash of another access token`() = runTest {
        val proof = createValidDpopProof(
            htm = "GET",
            htu = "https://auth.example.com/api/openid/userinfo",
            ath = ACCESS_TOKEN_HASH
        )
        val request = boundRequest(
            method = "GET",
            uri = URI.create("/api/openid/userinfo"),
            accessToken = "another-access-token"
        )

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.invalid_ath", exception.detailsId)
    }

    @Test
    fun `getAccessTokenHash - Hashes the access token as RFC 9449 does`() {
        assertEquals(ACCESS_TOKEN_HASH, dpopManager.getAccessTokenHash(ACCESS_TOKEN))
    }

    @Test
    fun `validateAccessTokenBinding - Accepts a token bound to no key presented as a bearer token`() = runTest {
        dpopManager.validateAccessTokenBinding(
            token = tokenBoundTo(null),
            presentedWithDpopScheme = false,
            proofs = listOf("not-a-proof"),
            boundRequest = userInfoRequest()
        )
    }

    @Test
    fun `validateAccessTokenBinding - Throws when a bound token is presented as a bearer token`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateAccessTokenBinding(
                token = tokenBoundTo(ecKey.computeThumbprint().toString()),
                presentedWithDpopScheme = false,
                proofs = listOf(createUserInfoProof()),
                boundRequest = userInfoRequest()
            )
        }
        assertEquals(INVALID_DPOP_PROOF, exception.errorCode)
        assertEquals("dpop.bound_token_as_bearer", exception.detailsId)
    }

    @Test
    fun `validateAccessTokenBinding - Throws when a token bound to no key is presented under the DPoP scheme`() =
        runTest {
            val exception = assertThrows<OAuth2Exception> {
                dpopManager.validateAccessTokenBinding(
                    token = tokenBoundTo(null),
                    presentedWithDpopScheme = true,
                    proofs = listOf(createUserInfoProof()),
                    boundRequest = userInfoRequest()
                )
            }
            assertEquals("dpop.unbound_token", exception.detailsId)
        }

    @Test
    fun `validateAccessTokenBinding - Throws when a bound token is presented with no proof`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateAccessTokenBinding(
                token = tokenBoundTo(ecKey.computeThumbprint().toString()),
                presentedWithDpopScheme = true,
                proofs = emptyList(),
                boundRequest = userInfoRequest()
            )
        }
        assertEquals("dpop.missing_header", exception.detailsId)
    }

    @Test
    fun `validateAccessTokenBinding - Throws when the proof is signed with another key`() = runTest {
        val otherEcKey = createOtherEcKey()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateAccessTokenBinding(
                token = tokenBoundTo(ecKey.computeThumbprint().toString()),
                presentedWithDpopScheme = true,
                proofs = listOf(createUserInfoProof(otherEcKey, ECDSASigner(otherEcKey))),
                boundRequest = userInfoRequest()
            )
        }
        assertEquals("dpop.mismatching_key", exception.detailsId)
    }

    @Test
    fun `validateAccessTokenBinding - Throws when the proof does not validate against the request`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateAccessTokenBinding(
                token = tokenBoundTo(ecKey.computeThumbprint().toString()),
                presentedWithDpopScheme = true,
                proofs = listOf(createValidDpopProof(htm = "GET", ath = ACCESS_TOKEN_HASH)),
                boundRequest = userInfoRequest()
            )
        }
        assertEquals("dpop.invalid_htu", exception.detailsId)
    }

    @Test
    fun `validateAccessTokenBinding - Accepts a bound token presented with a proof signed by its key`() = runTest {
        dpopManager.validateAccessTokenBinding(
            token = tokenBoundTo(ecKey.computeThumbprint().toString()),
            presentedWithDpopScheme = true,
            proofs = listOf(createUserInfoProof()),
            boundRequest = userInfoRequest()
        )
    }

    @Test
    fun `checkProofMatchesBinding - Accepts no proof for a token bound to no key`() {
        dpopManager.checkProofMatchesBinding(tokenBoundTo(null), null)
    }

    private companion object {
        /**
         * The access token RFC 9449 §7.1 presents in its example, and the `ath` its example proof carries for
         * it — a hash computed by the specification rather than by the code under test.
         */
        const val ACCESS_TOKEN = "Kz~8mXK1EalYznwH-LC-1fBAo.4Ljp~zsPE_NeO.gxU"
        const val ACCESS_TOKEN_HASH = "fUHyO2r2Z3DZ53EsNrWBb0xWXoaNy59IiKCAqksmQEo"
    }
}
