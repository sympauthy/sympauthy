package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.time.LocalDateTime.now

@Serdeable
@MappedEntity("crypto_keys")
data class CryptoKeysEntity(
    val algorithm: String,

    /**
     * The column is `NOT NULL` and holds an empty array for an algorithm with no public half: a null
     * [ByteArray] is bound as a `Byte[]`, which PostgreSQL types `smallint[]` and refuses against a
     * `bytea`. [com.sympauthy.business.mapper.StoredPublicKeyMapper] translates the empty array into
     * the null the business model spells absence with.
     */
    val publicKey: ByteArray,
    val publicKeyFormat: String?,

    val privateKey: ByteArray,
    val privateKeyFormat: String,

    val creationDate: LocalDateTime = now(),
) {
    @Id
    var name: String? = null
}
