package com.sympauthy.business.mapper

import com.sympauthy.business.model.key.CryptoKeys
import com.sympauthy.data.model.IndexedCryptoKeysEntity
import jakarta.inject.Singleton
import java.time.LocalDateTime

@Singleton
class IndexedCryptoKeysMapper : StoredPublicKeyMapper() {

    fun toCryptoKeys(entity: IndexedCryptoKeysEntity): CryptoKeys {
        return CryptoKeys(
            name = entity.name,
            algorithm = entity.algorithm,
            publicKey = toPublicKey(entity.publicKey),
            publicKeyFormat = entity.publicKeyFormat,
            privateKey = entity.privateKey.copyOf(),
            privateKeyFormat = entity.privateKeyFormat,
        )
    }

    fun toEntity(keys: CryptoKeys): IndexedCryptoKeysEntity {
        return IndexedCryptoKeysEntity(
            name = keys.name,
            algorithm = keys.algorithm,
            publicKey = toStoredPublicKey(keys.publicKey),
            publicKeyFormat = keys.publicKeyFormat,
            privateKey = keys.privateKey.copyOf(),
            privateKeyFormat = keys.privateKeyFormat,
            creationDate = LocalDateTime.now(),
        )
    }
}
