package com.sympauthy.business.model.key

/**
 * The generation of the cryptographic keys this server signs with, which a deployment picks one of
 * in `advanced.keys-generation-strategy`.
 */
interface CryptoKeysGenerationStrategy {

    /**
     * Generate cryptographic keys usable by the [algorithm] and identified by [name].
     *
     * The generation strategy must support multiple instances being run in a cluster.
     */
    suspend fun generateKeys(name: String, algorithm: KeyAlgorithm): CryptoKeys
}
