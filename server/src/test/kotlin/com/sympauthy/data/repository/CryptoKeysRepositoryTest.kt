package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.CryptoKeysEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The key pair stored under a name. Its identifier is that name and the database does not generate it,
 * so the insert has to carry it — the only entity in the model whose key is a string it was given.
 */
class CryptoKeysRepositoryTest {

    private val name = "crypto-keys-repository-test"
    private val otherName = "crypto-keys-repository-test-other"

    @ParameterizedTest(name = "save - Carries the assigned name and round-trips the key bytes on {0}")
    @EnumSource(Database::class)
    fun `save - Carries the assigned name and round-trips the key bytes`(database: Database) =
        withFixture(database) {
            val keys = repository<CryptoKeysRepository>()

            saveKeys(name)

            val stored = keys.findById(name)
            assertNotNull(stored)
            assertEquals(name, stored!!.name)
            assertEquals("RS256", stored.algorithm)
            assertArrayEquals(byteArrayOf(1, 2, 3), stored.publicKey)
            assertArrayEquals(byteArrayOf(4, 5, 6, 7), stored.privateKey)
            assertEquals("X.509", stored.publicKeyFormat)
            assertEquals(BASE_DATE, stored.creationDate)
        }

    /**
     * A symmetric secret has no public half, and [com.sympauthy.business.model.key.HMACKeyImpl] spells
     * that as an empty array rather than as the null the column and the entity both admit: PostgreSQL
     * types a null `ByteArray` parameter as `smallint[]` and refuses it against a `bytea`. This holds
     * the workaround in place — restore the null and the PostgreSQL run fails here.
     */
    @ParameterizedTest(name = "save - Round-trips the empty public half of a symmetric key on {0}")
    @EnumSource(Database::class)
    fun `save - Round-trips the empty public half of a symmetric key`(database: Database) =
        withFixture(database) {
            val keys = repository<CryptoKeysRepository>()

            saveKeys(name, publicKey = ByteArray(0))

            val stored = keys.findById(name)
            assertNotNull(stored)
            assertArrayEquals(ByteArray(0), stored!!.publicKey)
            assertNull(stored.publicKeyFormat)
            assertArrayEquals(byteArrayOf(4, 5, 6, 7), stored.privateKey)
        }

    @ParameterizedTest(name = "findByName - Finds the keys stored under the name on {0}")
    @EnumSource(Database::class)
    fun `findByName - Finds the keys stored under the name`(database: Database) = withFixture(database) {
        val keys = repository<CryptoKeysRepository>()
        saveKeys(name)
        saveKeys(otherName)

        assertEquals(name, keys.findByName(name)?.name)
        assertNull(keys.findByName("crypto-keys-repository-test-absent"))
    }

    private suspend fun RepositoryFixture.saveKeys(
        name: String,
        publicKey: ByteArray? = byteArrayOf(1, 2, 3)
    ) {
        val keys = repository<CryptoKeysRepository>()
        keys.save(
            CryptoKeysEntity(
                algorithm = "RS256",
                publicKey = publicKey,
                publicKeyFormat = "X.509".takeIf { publicKey?.isNotEmpty() == true },
                privateKey = byteArrayOf(4, 5, 6, 7),
                privateKeyFormat = "PKCS#8",
                creationDate = BASE_DATE
            ).apply { this.name = name }
        )
        deleteOnEnd { keys.deleteById(name) }
    }
}
