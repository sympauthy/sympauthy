package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime

@Serdeable
@MappedEntity("indexed_crypto_keys")
data class IndexedCryptoKeysEntity(
    val name: String,
    val algorithm: String,

    val publicKey: ByteArray?,
    val publicKeyFormat: String?,

    val privateKey: ByteArray,
    val privateKeyFormat: String?,

    val creationDate: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue
    var index: Int? = null
}
