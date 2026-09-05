package com.sympauthy.business.mapper

import com.sympauthy.business.model.key.CryptoKeys
import com.sympauthy.data.model.IndexedCryptoKeysEntity
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime

class IndexedCryptoKeysMapperTest {

    private val mapper = Mappers.getMapper(IndexedCryptoKeysMapper::class.java)

    @Test
    fun `toCryptoKeys - maps all fields`() {
        val entity = entity(publicKey = byteArrayOf(1, 2, 3))

        val keys = mapper.toCryptoKeys(entity)

        assertEquals("indexed-crypto-keys-mapper-test", keys.name)
        assertEquals("RSA", keys.algorithm)
        assertArrayEquals(byteArrayOf(1, 2, 3), keys.publicKey)
        assertEquals("X.509", keys.publicKeyFormat)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), keys.privateKey)
        assertEquals("PKCS#8", keys.privateKeyFormat)
    }

    @Test
    fun `toCryptoKeys - reads the empty public half as absent`() {
        val entity = entity(publicKey = ByteArray(0), publicKeyFormat = null)

        val keys = mapper.toCryptoKeys(entity)

        assertNull(keys.publicKey)
        assertNull(keys.publicKeyFormat)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), keys.privateKey)
    }

    @Test
    fun `toEntity - maps all fields`() {
        val keys = keys(publicKey = byteArrayOf(1, 2, 3))

        val entity = mapper.toEntity(keys)

        assertEquals("indexed-crypto-keys-mapper-test", entity.name)
        assertEquals("RSA", entity.algorithm)
        assertArrayEquals(byteArrayOf(1, 2, 3), entity.publicKey)
        assertEquals("X.509", entity.publicKeyFormat)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), entity.privateKey)
        assertEquals("PKCS#8", entity.privateKeyFormat)
        assertNull(entity.index)
    }

    @Test
    fun `toEntity - stores an absent public half as the empty array`() {
        val keys = keys(publicKey = null, publicKeyFormat = null)

        val entity = mapper.toEntity(keys)

        assertArrayEquals(ByteArray(0), entity.publicKey)
        assertNull(entity.publicKeyFormat)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), entity.privateKey)
    }

    private fun entity(
        publicKey: ByteArray,
        publicKeyFormat: String? = "X.509"
    ) = IndexedCryptoKeysEntity(
        name = "indexed-crypto-keys-mapper-test",
        algorithm = "RSA",
        publicKey = publicKey,
        publicKeyFormat = publicKeyFormat,
        privateKey = byteArrayOf(4, 5, 6, 7),
        privateKeyFormat = "PKCS#8",
        creationDate = LocalDateTime.of(2026, 1, 1, 0, 0)
    )

    private fun keys(
        publicKey: ByteArray?,
        publicKeyFormat: String? = "X.509"
    ) = CryptoKeys(
        name = "indexed-crypto-keys-mapper-test",
        algorithm = "RSA",
        publicKey = publicKey,
        publicKeyFormat = publicKeyFormat,
        privateKey = byteArrayOf(4, 5, 6, 7),
        privateKeyFormat = "PKCS#8"
    )
}
