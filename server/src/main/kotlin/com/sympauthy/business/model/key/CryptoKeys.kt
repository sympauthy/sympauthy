package com.sympauthy.business.model.key

/**
 * An object storing binary serialized cryptographic keys designed to be used by a cryptographic [algorithm].
 *
 * Depending on the [algorithm], this object will either contain only a private key or a public key and a private key.
 */
class CryptoKeys(
    /**
     * Identifier of this key.
     */
    val name: String,

    /**
     * Name of the [KeyAlgorithm] these keys were generated for. Stored as a string rather than the
     * enum: a row written by an earlier version may name an algorithm this one no longer offers.
     */
    val algorithm: String,

    /**
     * Public key serialized in a binary form.
     * Only present if the algorithm supports public key.
     */
    val publicKey: ByteArray?,

    /**
     * Format used to store the public key in a binary form.
     */
    val publicKeyFormat: String?,

    /**
     * Private key serialized in a binary form.
     * Always present: a symmetric algorithm keeps its one secret here.
     */
    val privateKey: ByteArray,

    /**
     * Format used to store the private key in a binary form.
     */
    val privateKeyFormat: String
)
