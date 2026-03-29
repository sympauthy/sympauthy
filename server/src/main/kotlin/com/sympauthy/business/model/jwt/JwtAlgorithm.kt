package com.sympauthy.business.model.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.key.CryptoKeys
import com.sympauthy.business.model.key.KeyAlgorithm
import com.sympauthy.business.model.key.KeyAlgorithm.RSA
import com.sympauthy.business.model.key.RSAKeyImpl
import com.sympauthy.business.model.key.getImpl

/**
 * Enumeration of all JWT signing algorithm supported by the project.
 */
enum class JwtAlgorithm(
    /**
     * Cryptographic algorithm used to sign.
     */
    val keyAlgorithm: KeyAlgorithm,
    val impl: JwtAlgorithmImpl
) {
    RS256(RSA, RS256AlgorithmImpl())
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

class RS256AlgorithmImpl : JwtAlgorithmImpl() {

    override fun unsafeInitialize(cryptoKeys: CryptoKeys): JwtSigningConfig {
        val rsaKeyImpl = RSA.getImpl<RSAKeyImpl>()
        val privateKey = rsaKeyImpl.toPrivateKey(cryptoKeys)
        val publicKey = rsaKeyImpl.toPublicKey(cryptoKeys)
        return JwtSigningConfig(
            algorithm = JWSAlgorithm.RS256,
            signer = RSASSASigner(privateKey),
            verifier = RSASSAVerifier(publicKey),
            keyId = cryptoKeys.name
        )
    }
}
