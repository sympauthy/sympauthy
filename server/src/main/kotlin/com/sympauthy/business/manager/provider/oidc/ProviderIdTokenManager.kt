package com.sympauthy.business.manager.provider.oidc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.provider.config.ProviderOidcConfig
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class ProviderIdTokenManager {

    fun validateAndExtractClaims(
        oidcConfig: ProviderOidcConfig,
        idTokenRaw: String,
        expectedNonce: String?
    ): Map<String, Any> {
        try {
            val jwkSource = JWKSourceBuilder.create<SecurityContext>(oidcConfig.jwksUri.toURL()).build()

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
                .issuer(oidcConfig.issuer.toString())
                .apply {
                    if (expectedNonce != null) {
                        claim("nonce", expectedNonce)
                    }
                }
                .build()

            jwtProcessor.jwtClaimsSetVerifier = DefaultJWTClaimsVerifier<SecurityContext>(
                setOf(oidcConfig.clientId),
                exactMatchClaims,
                requiredClaims,
                emptySet()
            )

            val claimsSet = jwtProcessor.process(idTokenRaw, null)

            return claimsSet.claims
                .filterValues { it != null }
                .mapValues { it.value!! }
        } catch (e: Exception) {
            throw businessExceptionOf(
                "provider.oidc.invalid_id_token",
                "providerId" to oidcConfig.clientId
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
