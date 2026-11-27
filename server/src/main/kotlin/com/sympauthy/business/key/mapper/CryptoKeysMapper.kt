package com.sympauthy.business.key.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.key.model.CryptoKeys
import com.sympauthy.data.model.CryptoKeysEntity
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Handle the mapping between the [CryptoKeysEntity] and the [CryptoKeys] business model.
 *
 * If the row holds no name, an internal [BusinessException] "mapper.crypto_keys.invalid_property" is
 * thrown.
 */
@Singleton
class CryptoKeysMapper : StoredPublicKeyMapper() {

    fun toCryptoKeys(entity: CryptoKeysEntity): CryptoKeys {
        return CryptoKeys(
            name = entity.name ?: throw internalBusinessExceptionOf(
                detailsId = "mapper.crypto_keys.invalid_property",
                values = arrayOf("property" to "name")
            ),
            algorithm = entity.algorithm,
            publicKey = toPublicKey(entity.publicKey),
            publicKeyFormat = entity.publicKeyFormat,
            privateKey = entity.privateKey.copyOf(),
            privateKeyFormat = entity.privateKeyFormat,
        )
    }

    fun toEntity(key: CryptoKeys, creationDate: LocalDateTime): CryptoKeysEntity {
        return CryptoKeysEntity(
            algorithm = key.algorithm,
            publicKey = toStoredPublicKey(key.publicKey),
            publicKeyFormat = key.publicKeyFormat,
            privateKey = key.privateKey.copyOf(),
            privateKeyFormat = key.privateKeyFormat,
            creationDate = creationDate,
        ).apply {
            name = key.name
        }
    }
}
