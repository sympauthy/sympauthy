package com.sympauthy.business.mapper

import org.mapstruct.Named

/**
 * The two spellings of the public half of a key, and the translation between them.
 *
 * A symmetric algorithm has no public half and the domain says so with `null`. Its column cannot:
 * `public_key` is `NOT NULL` and an empty array stands for the half that does not exist. Every
 * mapper of a key extends this one, so nothing above the mapper meets the empty array.
 */
abstract class StoredPublicKeyMapper {

    /**
     * The public key the domain reads for the one [publicKey] the row holds: the key itself, or
     * null where the row holds the empty array of an algorithm without a public half.
     */
    @Named("toPublicKey")
    fun toPublicKey(publicKey: ByteArray): ByteArray? = publicKey.takeIf(ByteArray::isNotEmpty)

    /**
     * The array the row holds for the [publicKey] the domain carries: the key itself, or the empty
     * array where the algorithm has no public half.
     */
    @Named("toStoredPublicKey")
    fun toStoredPublicKey(publicKey: ByteArray?): ByteArray = publicKey ?: ByteArray(0)
}
