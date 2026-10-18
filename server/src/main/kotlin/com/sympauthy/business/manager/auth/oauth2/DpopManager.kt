package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.DpopProof
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TOKEN
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.charset.StandardCharsets.US_ASCII
import java.security.MessageDigest
import java.text.ParseException
import java.time.Duration
import java.time.Instant

/**
 * Handles DPoP (Demonstrating Proof of Possession, RFC 9449): the proof a client presents at the token
 * endpoint to have its tokens bound to its key, and the proof it presents with a bound access token on every
 * request that token authorizes.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9449">RFC 9449</a>
 */
@Singleton
class DpopManager(
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    /**
     * Validate the DPoP proof presented on a request and return it as a [DpopProof] carrying the thumbprint
     * of the key it was signed with, or null when the caller presented none.
     *
     * [proofs] is every value sent under the [DPOP_HEADER] header, and [boundRequest] the request the proof
     * has to be bound to. Presenting more than one proof, or one that does not validate against
     * [boundRequest], raises an [com.sympauthy.api.exception.OAuth2Exception] carrying [INVALID_DPOP_PROOF].
     */
    suspend fun validateDpopProof(proofs: List<String>, boundRequest: DpopBoundRequest): DpopProof? {
        if (proofs.isEmpty()) {
            return null
        }
        if (proofs.size > 1) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.multiple_headers")
        }

        return validateProof(proofs.first(), boundRequest)
    }

    /**
     * Validate that the access token [token] is presented the way its binding requires, which is the whole of
     * what stops a token bound to a key being used without that key.
     *
     * [presentedWithDpopScheme] says whether the request carried [token] under the `DPoP` authentication
     * scheme rather than as a bearer token. [proofs] is every value sent under the [DPOP_HEADER] header, and
     * [boundRequest] the request they have to be bound to, naming [token] as the access token it presents.
     *
     * A token bound to no key and presented as a bearer token is accepted whatever [proofs] holds. Otherwise
     * an [com.sympauthy.api.exception.OAuth2Exception] is raised, carrying [INVALID_TOKEN] where the token is
     * presented under a scheme its binding refuses, and [INVALID_DPOP_PROOF] where its proof is what refuses
     * it, which are the errors a resource challenges each with, RFC 9449 §7.1:
     * - `dpop.bound_token_as_bearer`, [INVALID_TOKEN], where a token bound to a key is presented as a bearer
     *   token.
     * - `dpop.unbound_token`, [INVALID_TOKEN], where a token bound to no key is presented under the `DPoP`
     *   scheme.
     * - any refusal of [validateDpopProof], and `dpop.missing_header` or `dpop.mismatching_key` as
     *   [checkProofMatchesBinding] raises them, where a token bound to a key is presented under it.
     */
    suspend fun validateAccessTokenBinding(
        token: AuthenticationToken,
        presentedWithDpopScheme: Boolean,
        proofs: List<String>,
        boundRequest: DpopBoundRequest
    ) {
        // RFC 9449 §7.2: a token bound to a key is refused as a bearer token even beside a valid proof, or
        // the binding would be optional to whoever holds the token.
        if (!presentedWithDpopScheme) {
            if (token.dpopJkt != null) {
                throw oauth2ExceptionOf(INVALID_TOKEN, "dpop.bound_token_as_bearer")
            }
            return
        }
        if (token.dpopJkt == null) {
            throw oauth2ExceptionOf(INVALID_TOKEN, "dpop.unbound_token")
        }
        val proof = validateDpopProof(proofs, boundRequest)
        checkProofMatchesBinding(token, proof?.jkt)
    }

    /**
     * Check that a proof whose key has the thumbprint [proofJkt] — or no proof, where null — is one [token]
     * may be used with.
     *
     * A token bound to no key accepts any proof, and the absence of one. A token bound to a key raises an
     * [com.sympauthy.api.exception.OAuth2Exception] carrying [INVALID_DPOP_PROOF]: `dpop.missing_header`
     * where [proofJkt] is null, and `dpop.mismatching_key` where it names another key.
     */
    fun checkProofMatchesBinding(token: AuthenticationToken, proofJkt: String?) {
        if (token.dpopJkt == null) {
            return
        }
        if (proofJkt == null) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.missing_header")
        }
        if (token.dpopJkt != proofJkt) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.mismatching_key")
        }
    }

    internal suspend fun validateProof(encodedProof: String, boundRequest: DpopBoundRequest): DpopProof {
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
        if (!htm.equals(boundRequest.method, ignoreCase = true)) {
            throw oauth2ExceptionOf(
                INVALID_DPOP_PROOF, "dpop.invalid_htm",
                "proofHtm" to htm, "requestMethod" to boundRequest.method
            )
        }

        // Validate htu matches request URI (without query and fragment)
        val requestUri = getRequestUri(boundRequest)
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

        // Validate ath binds the proof to the access token presented beside it. Read as an untyped claim
        // because one that is not a string is a proof failing this check, not a request this server fails.
        if (boundRequest.accessToken != null) {
            val ath = claims.getClaim("ath") as? String
            if (ath != getAccessTokenHash(boundRequest.accessToken)) {
                throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.invalid_ath")
            }
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
    internal suspend fun verifySignature(signedJwt: SignedJWT, jwk: JWK) {
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
        } catch (_: Exception) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.invalid_signature")
        }
    }

    /**
     * Returns the full request URI without query string and fragment, as required by RFC 9449.
     * Uses [EnabledUrlsConfig.getUri] to build the absolute URI from the configured root.
     */
    internal suspend fun getRequestUri(boundRequest: DpopBoundRequest): String {
        return uncheckedUrlsConfig.orThrow().getUri(boundRequest.uri.path).toString()
    }

    /**
     * Returns the value the `ath` claim of a proof presented with [accessToken] must carry: the
     * base64url-encoded SHA-256 hash of its ASCII encoding, as RFC 9449 §4.2 defines it.
     */
    internal fun getAccessTokenHash(accessToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(US_ASCII))
        return Base64URL.encode(digest).toString()
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
