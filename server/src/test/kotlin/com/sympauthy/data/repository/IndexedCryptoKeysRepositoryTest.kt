package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.IndexedCryptoKeysEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The keys held in a numbered series. Its identifier is the one integer key the database generates
 * itself, spelled `serial` under PostgreSQL and `AUTO_INCREMENT` under H2 — the sharpest divergence
 * between the two schemas, and one only an insert can settle.
 */
class IndexedCryptoKeysRepositoryTest {

    private val name = "indexed-crypto-keys-repository-test"
    private val otherName = "indexed-crypto-keys-repository-test-other"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Generates the index and round-trips the key bytes`(database: Database) = withFixture(database) {
        val keys = repository<IndexedCryptoKeysRepository>()

        val index = saveKeys(name)

        val stored = keys.findById(index)
        assertNotNull(stored)
        assertEquals(index, stored!!.index)
        assertEquals(name, stored.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), stored.publicKey)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), stored.privateKey)
        assertEquals(BASE_DATE, stored.creationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Hands each row a distinct index`(database: Database) = withFixture(database) {
        val first = saveKeys(name)
        val second = saveKeys(name)

        assertTrue(first != second)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByNameAndAlgorithm - Returns the series under both keys`(database: Database) =
        withFixture(database) {
            val keys = repository<IndexedCryptoKeysRepository>()
            val first = saveKeys(name)
            val second = saveKeys(name)
            saveKeys(name, algorithm = "ES256")
            saveKeys(otherName)

            val found = keys.findByNameAndAlgorithm(name, "RS256")

            assertEquals(setOf(first, second), found.map { it.index!! }.toSet())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByNameAndAlgorithm - Returns nothing when the series is unknown`(database: Database) =
        withFixture(database) {
            saveKeys(name)

            val found = repository<IndexedCryptoKeysRepository>()
                .findByNameAndAlgorithm("indexed-crypto-keys-repository-test-absent", "RS256")

            assertTrue(found.isEmpty())
        }

    /** The empty public half a symmetric key carries, for the reason [CryptoKeysRepositoryTest] gives. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the empty public half of a symmetric key`(database: Database) =
        withFixture(database) {
            val keys = repository<IndexedCryptoKeysRepository>()

            val index = saveKeys(name, publicKey = ByteArray(0))

            val stored = keys.findById(index)
            assertNotNull(stored)
            assertArrayEquals(ByteArray(0), stored!!.publicKey)
            assertNull(stored.publicKeyFormat)
        }

    private suspend fun RepositoryFixture.saveKeys(
        name: String,
        algorithm: String = "RS256",
        publicKey: ByteArray? = byteArrayOf(1, 2, 3)
    ): Int {
        val keys = repository<IndexedCryptoKeysRepository>()
        return keys.save(
            IndexedCryptoKeysEntity(
                name = name,
                algorithm = algorithm,
                publicKey = publicKey,
                publicKeyFormat = "X.509".takeIf { publicKey?.isNotEmpty() == true },
                privateKey = byteArrayOf(4, 5, 6, 7),
                privateKeyFormat = "PKCS#8",
                creationDate = BASE_DATE
            )
        ).index!!.also { index -> deleteOnEnd { keys.deleteById(index) } }
    }
}
