package com.sympauthy.business.model.key

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse.SIGNATURE
import com.nimbusds.jose.jwk.RSAKey
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.localizedExceptionOf
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

enum class KeyAlgorithm(
    val impl: KeyAlgorithmImpl,
    /**
     * True if the algorithm uses asymmetric key.
     */
    val supportsPublicKey: Boolean
) {
    RSA(RSAKeyImpl(), true)
}

inline fun <reified T : KeyAlgorithmImpl> KeyAlgorithm.getImpl(): T {
    return this.impl as T
}

sealed class KeyAlgorithmImpl {

    /**
     * Generate random keys.
     */
    abstract fun generate(name: String): CryptoKeys

    /**
     * Serialize the public key into a JSON Web key([JWK]).
     *
     * Throws a [LocalizedException] if the algorithm does not support a public key.
     */
    open fun serializePublicKey(keys: CryptoKeys): JWK {
        throw localizedExceptionOf("keyalgorithm.public_key.unsupported")
    }
}

class RSAKeyImpl : KeyAlgorithmImpl() {

    override fun generate(name: String): CryptoKeys {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        return CryptoKeys(
            name = name,
            algorithm = "RSA",
            publicKey = keyPair.public.encoded,
            publicKeyFormat = keyPair.public.format,
            privateKey = keyPair.private.encoded,
            privateKeyFormat = keyPair.private.format
        )
    }

    internal fun toPublicKey(keys: CryptoKeys): RSAPublicKey {
        if (keys.publicKey == null || keys.publicKeyFormat == null) {
            throw localizedExceptionOf(
                "key.missing_public_key",
                "name" to keys.name
            )
        }
        val keySpec = getKeySpec(
            name = keys.name,
            key = keys.publicKey,
            format = keys.publicKeyFormat
        )
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePublic(keySpec) as RSAPublicKey
    }

    internal fun toPrivateKey(keys: CryptoKeys): RSAPrivateKey {
        val keySpec = getKeySpec(
            name = keys.name,
            key = keys.privateKey,
            format = keys.privateKeyFormat
        )
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePrivate(keySpec) as RSAPrivateKey
    }

    override fun serializePublicKey(keys: CryptoKeys): JWK {
        return RSAKey.Builder(toPublicKey(keys))
            .keyID(keys.name)
            .keyUse(SIGNATURE)
            .build()
    }
}

fun getKeySpec(
    name: String,
    key: ByteArray,
    format: String
): KeySpec {
    return when (format) {
        "PKCS#8" -> PKCS8EncodedKeySpec(key)
        "X.509" -> X509EncodedKeySpec(key)
        else -> throw localizedExceptionOf(
            "key.unsupported_key_spec",
            "name" to name,
            "keySpec" to format
        )
    }
}
