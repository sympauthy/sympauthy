package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.model.oauth2.DpopProof
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton
import java.text.ParseException
import java.time.Duration
import java.time.Instant

/**
 * Handles DPoP (Demonstrating Proof of Possession, RFC 9449) proof validation
 * at the token endpoint.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9449">RFC 9449</a>
 */
@Singleton
class DpopManager {

    /**
     * Extracts and validates the DPoP proof from the request, if present.
     *
     * @return a [DpopProof] containing the JWK thumbprint, or null if no DPoP header is present.
     * @throws com.sympauthy.api.exception.OAuth2Exception if the proof is present but invalid.
     */
    fun validateDpopProof(request: HttpRequest<*>): DpopProof? {
        val dpopHeaders = request.headers.getAll(DPOP_HEADER)
        if (dpopHeaders.isNullOrEmpty()) {
            return null
        }
        if (dpopHeaders.size > 1) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.multiple_headers")
        }

        val proofJwt = dpopHeaders.first()
        return validateProof(proofJwt, request)
    }

    internal fun validateProof(encodedProof: String, request: HttpRequest<*>): DpopProof {
        val signedJwt = try {
            SignedJWT.parse(encodedProof)
        } catch (e: ParseException) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.malformed")
        }

        val header = signedJwt.header

        // Validate typ header
        val typ = header.type?.toString()
        if (typ != DPOP_JWT_TYPE) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.invalid_type")
        }

        // Validate alg header
        val alg = header.algorithm?.name
        if (alg == null || alg !in SUPPORTED_ALGORITHMS) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.unsupported_algorithm")
        }

        // Validate jwk header and extract public key
        val jwk = header.jwk
        if (jwk == null) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.missing_claims")
        }

        if (jwk.isPrivate) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.private_key_in_header")
        }

        // Verify signature against the public key in jwk header
        verifySignature(signedJwt, jwk)

        // Validate required claims
        val claims = signedJwt.jwtClaimsSet
        val jti = claims.jwtid
        val htm = claims.getStringClaim("htm")
        val htu = claims.getStringClaim("htu")
        val iat = claims.issueTime

        if (jti.isNullOrBlank() || htm.isNullOrBlank() || htu.isNullOrBlank() || iat == null) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.missing_claims")
        }

        // Validate htm matches request method
        if (!htm.equals(request.method.name, ignoreCase = true)) {
            throw oauth2ExceptionOf(
                INVALID_DPOP_PROOF, "dpop.invalid_htm",
                "proofHtm" to htm, "requestMethod" to request.method.name
            )
        }

        // Validate htu matches request URI (without query and fragment)
        val requestUri = getRequestUri(request)
        if (htu != requestUri) {
            throw oauth2ExceptionOf(
                INVALID_DPOP_PROOF, "dpop.invalid_htu",
                "proofHtu" to htu, "requestUri" to requestUri
            )
        }

        // Validate iat is within acceptable window
        val now = Instant.now()
        val issuedAt = iat.toInstant()
        if (Duration.between(issuedAt, now).abs() > MAX_PROOF_AGE) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.expired")
        }

        // Compute JWK SHA-256 Thumbprint (RFC 7638)
        val thumbprint = jwk.computeThumbprint()
        val jkt = thumbprint.toString()

        return DpopProof(jkt = jkt)
    }

    /**
     * Verify the JWT signature using the public key from the jwk header.
     * Uses nimbus JOSE library which correctly handles JWS signature formats
     * (including ECDSA R||S encoding).
     */
    internal fun verifySignature(signedJwt: SignedJWT, jwk: JWK) {
        try {
            val verifier: JWSVerifier = when (jwk) {
                is RSAKey -> RSASSAVerifier(jwk)
                is ECKey -> ECDSAVerifier(jwk)
                else -> throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.unsupported_algorithm")
            }

            if (!signedJwt.verify(verifier)) {
                throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.invalid_signature")
            }
        } catch (e: com.sympauthy.api.exception.OAuth2Exception) {
            throw e
        } catch (e: Exception) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.invalid_signature")
        }
    }

    /**
     * Returns the request URI without query string and fragment, as required by RFC 9449.
     */
    internal fun getRequestUri(request: HttpRequest<*>): String {
        val uri = request.uri
        return "${uri.scheme}://${uri.authority}${uri.path}"
    }

    companion object {
        const val DPOP_HEADER = "DPoP"
        const val DPOP_JWT_TYPE = "dpop+jwt"

        val MAX_PROOF_AGE: Duration = Duration.ofSeconds(60)

        val SUPPORTED_ALGORITHMS = setOf(
            "RS256", "RS384", "RS512",
            "ES256", "ES384", "ES512",
            "PS256", "PS384", "PS512"
        )
    }
}
