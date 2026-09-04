package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.key.CryptoKeys
import com.sympauthy.data.model.IndexedCryptoKeysEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Mappings

@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class IndexedCryptoKeysMapper : StoredPublicKeyMapper() {

    @Mapping(source = "publicKey", target = "publicKey", qualifiedByName = ["toPublicKey"])
    abstract fun toCryptoKeys(entity: IndexedCryptoKeysEntity): CryptoKeys

    @Mappings(
        Mapping(source = "publicKey", target = "publicKey", qualifiedByName = ["toStoredPublicKey"]),
        Mapping(target = "index", ignore = true),
        Mapping(target = "creationDate", expression = "java(java.time.LocalDateTime.now())")
    )
    abstract fun toEntity(keys: CryptoKeys): IndexedCryptoKeysEntity
}
