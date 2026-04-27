package com.sympauthy.business.manager.provider.openidconnect

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Validates and extracts claims from ID tokens returned by OpenID Connect providers.
 *
 * Verification includes signature validation against the provider's JWKS, and standard claim checks:
 * issuer, audience, expiration, and nonce (if provided).
 */
@Singleton
class ProviderIdTokenManager {

    /**
     * @param accessToken the access token from the token response, used to validate the `at_hash` claim if present.
     */
    suspend fun validateAndExtractClaims(
        openIdConnectConfig: ProviderOpenIdConnectConfig,
        idTokenRaw: String,
        accessToken: String,
        expectedNonce: String?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val jwkSource = JWKSourceBuilder.create<SecurityContext>(openIdConnectConfig.jwksUri.toURL()).build()

            val jwtProcessor = DefaultJWTProcessor<SecurityContext>()
            jwtProcessor.jwsKeySelector = JWSVerificationKeySelector(
                SUPPORTED_ALGORITHMS,
                jwkSource
            )

            val requiredClaims = mutableSetOf("sub", "iss", "aud", "exp", "iat")
            if (expectedNonce != null) {
                requiredClaims.add("nonce")
            }
            val exactMatchClaims = JWTClaimsSet.Builder()
                .issuer(openIdConnectConfig.issuer.toString())
                .apply {
                    if (expectedNonce != null) {
                        claim("nonce", expectedNonce)
                    }
                }
                .build()

            jwtProcessor.jwtClaimsSetVerifier = DefaultJWTClaimsVerifier<SecurityContext>(
                setOf(openIdConnectConfig.clientId),
                exactMatchClaims,
                requiredClaims,
                emptySet()
            )

            val claimsSet = jwtProcessor.process(idTokenRaw, null)

            // Validate at_hash if present (OIDC Core §3.1.3.8)
            val atHash = claimsSet.getStringClaim("at_hash")
            if (atHash != null) {
                val signedJwt = SignedJWT.parse(idTokenRaw)
                val alg = signedJwt.header.algorithm
                validateAtHash(atHash, accessToken, alg, openIdConnectConfig.clientId)
            }

            claimsSet.claims
                .filterValues { it != null }
                .mapValues { it.value!! }
        } catch (e: com.sympauthy.business.exception.BusinessException) {
            throw e
        } catch (e: Exception) {
            throw businessExceptionOf(
                "provider.openid_connect.invalid_id_token",
                "providerId" to openIdConnectConfig.clientId,
                "reason" to (e.message ?: "unknown")
            )
        }
    }

    /**
     * Validate the `at_hash` claim against the access token (OIDC Core §3.1.3.8).
     * The `at_hash` is the base64url-encoded left half of the hash of the access token,
     * using the hash algorithm from the ID token's signing algorithm.
     */
    private fun validateAtHash(atHash: String, accessToken: String, alg: JWSAlgorithm, providerId: String) {
        val hashAlgorithm = when {
            alg.name.endsWith("256") -> "SHA-256"
            alg.name.endsWith("384") -> "SHA-384"
            alg.name.endsWith("512") -> "SHA-512"
            else -> return // Unknown algorithm, skip validation
        }
        val digest = MessageDigest.getInstance(hashAlgorithm)
        val fullHash = digest.digest(accessToken.toByteArray(Charsets.US_ASCII))
        val leftHalf = fullHash.copyOf(fullHash.size / 2)
        val expectedAtHash = Base64URL.encode(leftHalf).toString()
        if (atHash != expectedAtHash) {
            throw businessExceptionOf(
                "provider.openid_connect.invalid_at_hash",
                "providerId" to providerId
            )
        }
    }

    companion object {
        private val SUPPORTED_ALGORITHMS = setOf(
            JWSAlgorithm.RS256,
            JWSAlgorithm.RS384,
            JWSAlgorithm.RS512,
            JWSAlgorithm.ES256,
            JWSAlgorithm.ES384,
            JWSAlgorithm.ES512,
            JWSAlgorithm.PS256,
            JWSAlgorithm.PS384,
            JWSAlgorithm.PS512
        )
    }
}