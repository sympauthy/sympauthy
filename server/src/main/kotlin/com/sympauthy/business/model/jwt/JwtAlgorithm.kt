package com.sympauthy.business.model.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.key.*
import com.sympauthy.business.model.key.KeyAlgorithm.EC
import com.sympauthy.business.model.key.KeyAlgorithm.HMAC
import com.sympauthy.business.model.key.KeyAlgorithm.RSA

/**
 * Enumeration of all JWT signing algorithms supported by the project.
 *
 * Includes the algorithms required and recommended by the OAuth 2.1 specification:
 * - RS256: RSA PKCS#1 v1.5 with SHA-256 (required by OpenID Connect Core)
 * - ES256: ECDSA with P-256 and SHA-256 (recommended by OAuth 2.1)
 * - PS256: RSASSA-PSS with SHA-256 (recommended replacement for RS256)
 */
enum class JwtAlgorithm(
    /**
     * Cryptographic algorithm used to generate and store the signing keys.
     */
    val keyAlgorithm: KeyAlgorithm,
    val impl: JwtAlgorithmImpl,
    /**
     * True if signing the same payload with the same key always produces the same signature.
     * Deterministic algorithms are required for the private JWT algorithm because the provider
     * nonce flow relies on reconstructing an identical JWT at callback time.
     */
    val deterministic: Boolean
) {
    RS256(RSA, RSAAlgorithmImpl(JWSAlgorithm.RS256), deterministic = true),
    PS256(RSA, RSAAlgorithmImpl(JWSAlgorithm.PS256), deterministic = false),
    ES256(EC, ES256AlgorithmImpl(), deterministic = false),
    HS256(HMAC, HS256AlgorithmImpl(), deterministic = true)
}

/**
 * Configuration holder for JWT signing and verification.
 */
data class JwtSigningConfig(
    val algorithm: JWSAlgorithm,
    val signer: JWSSigner,
    val verifier: JWSVerifier,
    val keyId: String
)

sealed class JwtAlgorithmImpl {

    /**
     * Initialize the [JwtSigningConfig] with the signing keys contained in [cryptoKeys].
     */
    fun initializeWithKeys(cryptoKeys: CryptoKeys): JwtSigningConfig {
        return try {
            unsafeInitialize(cryptoKeys)
        } catch (e: BusinessException) {
            throw e
        } catch (t: Throwable) {
            throw BusinessException(
                recoverable = false,
                detailsId = "jwt.invalid_key",
                values = mapOf(
                    "name" to cryptoKeys.name
                ),
                throwable = t
            )
        }
    }

    protected abstract fun unsafeInitialize(cryptoKeys: CryptoKeys): JwtSigningConfig
}

/**
 * Implementation for RSA-based JWT algorithms (RS256, PS256).
 * Both use the same RSA key pair but differ in the padding scheme.
 */
class RSAAlgorithmImpl(
    private val jwsAlgorithm: JWSAlgorithm
) : JwtAlgorithmImpl() {

    override fun unsafeInitialize(cryptoKeys: CryptoKeys): JwtSigningConfig {
        val rsaKeyImpl = RSA.getImpl<RSAKeyImpl>()
        val privateKey = rsaKeyImpl.toPrivateKey(cryptoKeys)
        val publicKey = rsaKeyImpl.toPublicKey(cryptoKeys)
        return JwtSigningConfig(
            algorithm = jwsAlgorithm,
            signer = RSASSASigner(privateKey),
            verifier = RSASSAVerifier(publicKey),
            keyId = cryptoKeys.name
        )
    }
}

/**
 * Implementation for ECDSA P-256 with SHA-256.
 */
class ES256AlgorithmImpl : JwtAlgorithmImpl() {

    override fun unsafeInitialize(cryptoKeys: CryptoKeys): JwtSigningConfig {
        val ecKeyImpl = EC.getImpl<ECKeyImpl>()
        val privateKey = ecKeyImpl.toPrivateKey(cryptoKeys)
        val publicKey = ecKeyImpl.toPublicKey(cryptoKeys)
        return JwtSigningConfig(
            algorithm = JWSAlgorithm.ES256,
            signer = ECDSASigner(privateKey),
            verifier = ECDSAVerifier(publicKey),
            keyId = cryptoKeys.name
        )
    }
}

/**
 * Implementation for HMAC with SHA-256.
 * Uses a symmetric secret key for both signing and verification.
 */
class HS256AlgorithmImpl : JwtAlgorithmImpl() {

    override fun unsafeInitialize(cryptoKeys: CryptoKeys): JwtSigningConfig {
        val hmacKeyImpl = HMAC.getImpl<HMACKeyImpl>()
        val secretKey = hmacKeyImpl.toSecretKey(cryptoKeys)
        return JwtSigningConfig(
            algorithm = JWSAlgorithm.HS256,
            signer = MACSigner(secretKey),
            verifier = MACVerifier(secretKey),
            keyId = cryptoKeys.name
        )
    }
}
