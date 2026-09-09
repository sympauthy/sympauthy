package com.sympauthy.business.model.key

/**
 * The generation of the cryptographic keys this server signs with, as a deployment may pick one of
 * them in `advanced.keys-generation-strategy`.
 *
 * It is the interface a setting selects an implementation from, so it lives here rather than beside
 * the implementations: `business.model` is the only part of `business` the configuration layer may
 * name. The implementations are in `business.manager`, each published under the word an operator
 * writes to select it.
 */
interface CryptoKeysGenerationStrategy {

    /**
     * Generate cryptographic keys usable by the [algorithm] and identified by [name].
     *
     * The generation strategy must support multiple instances being run in a cluster.
     */
    suspend fun generateKeys(name: String, algorithm: KeyAlgorithm): CryptoKeys
}
