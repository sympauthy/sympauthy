package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.*

class DpopManagerTest {

    private val dpopManager = DpopManager()

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

    private fun mockRequest(
        method: HttpMethod = HttpMethod.POST,
        uri: URI = URI.create("https://auth.example.com/api/oauth2/token"),
        dpopHeaders: List<String> = emptyList()
    ): HttpRequest<*> {
        val headers = mockk<HttpHeaders> {
            every { getAll("DPoP") } returns dpopHeaders
        }
        return mockk {
            every { this@mockk.headers } returns headers
            every { this@mockk.method } returns method
            every { this@mockk.uri } returns uri
            every { this@mockk.methodName } returns method.name
        }
    }

    private fun createValidDpopProof(
        htm: String = "POST",
        htu: String = "https://auth.example.com/api/oauth2/token",
        iat: Instant = Instant.now(),
        jti: String = UUID.randomUUID().toString(),
        typ: String = "dpop+jwt",
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
            .build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(signerOverride)
        return signedJwt.serialize()
    }

    // --- validateDpopProof tests ---

    @Test
    fun `validateDpopProof - Returns null when no DPoP header`() {
        val request = mockRequest(dpopHeaders = emptyList())
        assertNull(dpopManager.validateDpopProof(request))
    }

    @Test
    fun `validateDpopProof - Throws when multiple DPoP headers`() {
        val request = mockRequest(dpopHeaders = listOf("proof1", "proof2"))
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateDpopProof(request)
        }
        assertEquals(INVALID_DPOP_PROOF, exception.errorCode)
        assertEquals("dpop.multiple_headers", exception.detailsId)
    }

    @Test
    fun `validateDpopProof - Returns DpopProof on valid proof`() {
        val proof = createValidDpopProof()
        val request = mockRequest(dpopHeaders = listOf(proof))
        val result = dpopManager.validateDpopProof(request)

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

    // --- validateProof: malformed ---

    @Test
    fun `validateProof - Throws on malformed JWT`() {
        val request = mockRequest()
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof("not-a-jwt", request)
        }
        assertEquals("dpop.malformed", exception.detailsId)
    }

    // --- validateProof: typ ---

    @Test
    fun `validateProof - Throws when typ is not dpop+jwt`() {
        val proof = createValidDpopProof(typ = "JWT")
        val request = mockRequest()
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.invalid_type", exception.detailsId)
    }

    // --- validateProof: alg ---

    @Test
    fun `validateProof - Throws on unsupported algorithm`() {
        // Create a proof with HS256 (symmetric, not allowed)
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
        val request = mockRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.unsupported_algorithm", exception.detailsId)
    }

    // --- validateProof: missing claims ---

    @Test
    fun `validateProof - Throws when jti is missing`() {
        val proof = createDpopProofWithClaims(
            JWTClaimsSet.Builder()
                // no jti
                .claim("htm", "POST")
                .claim("htu", "https://auth.example.com/api/oauth2/token")
                .issueTime(Date())
                .build()
        )
        val request = mockRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.missing_claims", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when htm is missing`() {
        val proof = createDpopProofWithClaims(
            JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                // no htm
                .claim("htu", "https://auth.example.com/api/oauth2/token")
                .issueTime(Date())
                .build()
        )
        val request = mockRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.missing_claims", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when htu is missing`() {
        val proof = createDpopProofWithClaims(
            JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", "POST")
                // no htu
                .issueTime(Date())
                .build()
        )
        val request = mockRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.missing_claims", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when iat is missing`() {
        val proof = createDpopProofWithClaims(
            JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", "POST")
                .claim("htu", "https://auth.example.com/api/oauth2/token")
                // no iat
                .build()
        )
        val request = mockRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.missing_claims", exception.detailsId)
    }

    // --- validateProof: htm mismatch ---

    @Test
    fun `validateProof - Throws when htm does not match request method`() {
        val proof = createValidDpopProof(htm = "GET")
        val request = mockRequest(method = HttpMethod.POST)

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.invalid_htm", exception.detailsId)
    }

    // --- validateProof: htu mismatch ---

    @Test
    fun `validateProof - Throws when htu does not match request URI`() {
        val proof = createValidDpopProof(htu = "https://other.example.com/api/oauth2/token")
        val request = mockRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.invalid_htu", exception.detailsId)
    }

    @Test
    fun `validateProof - Strips query string from request URI for htu comparison`() {
        val proof = createValidDpopProof(htu = "https://auth.example.com/api/oauth2/token")
        val request = mockRequest(uri = URI.create("https://auth.example.com/api/oauth2/token?foo=bar"))

        val result = dpopManager.validateProof(proof, request)
        assertNotNull(result)
    }

    // --- validateProof: iat window ---

    @Test
    fun `validateProof - Throws when iat is too old`() {
        val proof = createValidDpopProof(iat = Instant.now().minusSeconds(120))
        val request = mockRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.expired", exception.detailsId)
    }

    @Test
    fun `validateProof - Throws when iat is too far in the future`() {
        val proof = createValidDpopProof(iat = Instant.now().plusSeconds(120))
        val request = mockRequest()

        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(proof, request)
        }
        assertEquals("dpop.expired", exception.detailsId)
    }

    @Test
    fun `validateProof - Accepts iat within acceptable window`() {
        val proof = createValidDpopProof(iat = Instant.now().minusSeconds(30))
        val request = mockRequest()

        val result = dpopManager.validateProof(proof, request)
        assertNotNull(result)
    }

    // --- validateProof: signature ---

    @Test
    fun `validateProof - Throws when signature is invalid`() {
        val proof = createValidDpopProof()
        // Tamper with the payload to invalidate the signature
        val parts = proof.split(".")
        val tamperedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"jti\":\"tampered\",\"htm\":\"POST\",\"htu\":\"https://auth.example.com/api/oauth2/token\",\"iat\":${Instant.now().epochSecond}}".toByteArray())
        val tamperedProof = "${parts[0]}.$tamperedPayload.${parts[2]}"

        val request = mockRequest()
        val exception = assertThrows<OAuth2Exception> {
            dpopManager.validateProof(tamperedProof, request)
        }
        assertEquals("dpop.invalid_signature", exception.detailsId)
    }

    // --- validateProof: happy path ---

    @Test
    fun `validateProof - Returns consistent jkt for the same key`() {
        val proof1 = createValidDpopProof(jti = UUID.randomUUID().toString())
        val proof2 = createValidDpopProof(jti = UUID.randomUUID().toString())
        val request = mockRequest()

        val result1 = dpopManager.validateProof(proof1, request)
        val result2 = dpopManager.validateProof(proof2, request)

        assertEquals(result1!!.jkt, result2!!.jkt)
    }

    @Test
    fun `validateProof - Returns different jkt for different keys`() {
        val otherKeyPairGen = KeyPairGenerator.getInstance("EC")
        otherKeyPairGen.initialize(ECGenParameterSpec("secp256r1"))
        val otherKeyPair = otherKeyPairGen.generateKeyPair()
        val otherEcKey = ECKey.Builder(Curve.P_256, otherKeyPair.public as ECPublicKey)
            .privateKey(otherKeyPair.private as ECPrivateKey)
            .keyUse(KeyUse.SIGNATURE)
            .build()
        val otherSigner = ECDSASigner(otherEcKey)

        val proof1 = createValidDpopProof()
        val proof2 = createValidDpopProof(ecKeyOverride = otherEcKey, signerOverride = otherSigner)
        val request = mockRequest()

        val result1 = dpopManager.validateProof(proof1, request)
        val result2 = dpopManager.validateProof(proof2, request)

        assertNotEquals(result1!!.jkt, result2!!.jkt)
    }

    // --- getRequestUri ---

    @Test
    fun `getRequestUri - Strips query and fragment`() {
        val request = mockRequest(uri = URI.create("https://auth.example.com/api/oauth2/token?code=abc&state=xyz"))
        assertEquals("https://auth.example.com/api/oauth2/token", dpopManager.getRequestUri(request))
    }

    @Test
    fun `getRequestUri - Preserves port`() {
        val request = mockRequest(uri = URI.create("https://auth.example.com:8443/api/oauth2/token"))
        assertEquals("https://auth.example.com:8443/api/oauth2/token", dpopManager.getRequestUri(request))
    }
}
