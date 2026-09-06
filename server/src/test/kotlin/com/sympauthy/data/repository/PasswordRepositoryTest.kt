package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.PasswordEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/** The salt and the hash a password is stored as, both byte arrays. */
class PasswordRepositoryTest {

    private val salt = byteArrayOf(1, 2, 3, 4)
    private val hashedPassword = byteArrayOf(9, 8, 7, 6, 5)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the salt and the hash`(database: Database) = withFixture(database) {
        val passwords = repository<PasswordRepository>()
        val userId = newUser()
        val id = savePassword(userId)

        val stored = passwords.findById(id)

        assertNotNull(stored)
        assertArrayEquals(salt, stored!!.salt)
        assertArrayEquals(hashedPassword, stored.hashedPassword)
        assertEquals(userId, stored.userId)
        assertEquals(BASE_DATE, stored.creationDate)
        assertNull(stored.expirationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserId - Returns the passwords of the user`(database: Database) = withFixture(database) {
        val passwords = repository<PasswordRepository>()
        val userId = newUser()
        val otherUserId = newUser()
        val id = savePassword(userId)
        savePassword(otherUserId)

        val found = passwords.findByUserId(userId)

        assertEquals(listOf(id), found.map { it.id!! })
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserId - Returns nothing for a user with no password`(database: Database) =
        withFixture(database) {
            val userId = newUser()

            assertTrue(repository<PasswordRepository>().findByUserId(userId).isEmpty())
        }

    private suspend fun RepositoryFixture.savePassword(
        userId: UUID,
        expirationDate: LocalDateTime? = null,
        sessionId: UUID? = null
    ): UUID {
        val passwords = repository<PasswordRepository>()
        return passwords.save(
            PasswordEntity(
                userId = userId,
                salt = salt,
                hashedPassword = hashedPassword,
                creationDate = BASE_DATE,
                expirationDate = expirationDate,
                sessionId = sessionId
            )
        ).id!!.also { id -> deleteOnEnd { passwords.deleteById(id) } }
    }
}
