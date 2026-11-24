package com.sympauthy.business.password.manager

import com.sympauthy.business.password.model.HashedPassword
import com.sympauthy.business.user.model.User
import com.sympauthy.business.user.model.UserStatus
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.data.model.PasswordEntity
import com.sympauthy.data.repository.ObjectLockRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.platform.lock.HeldStripes
import com.sympauthy.platform.lock.LockKey
import com.sympauthy.platform.lock.LockManager
import com.sympauthy.platform.monitoring.telemetry.manager.LockMetrics
import com.sympauthy.platform.random.RandomGenerator
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*

@Suppress("unused")
@ExtendWith(MockKExtension::class)
class PasswordManagerTest {

    @MockK
    lateinit var hashGenerator: PasswordHashGenerator

    @MockK
    lateinit var randomGenerator: RandomGenerator

    @MockK
    lateinit var passwordRepository: PasswordRepository

    @MockK
    lateinit var uncheckedAdvancedConfig: AdvancedConfig

    private val objectLockRepository = mockk<ObjectLockRepository>(relaxed = true)

    @SpyK
    var lockManager = LockManager(objectLockRepository, HeldStripes(), LockMetrics(OpenTelemetry.noop()))

    @InjectMockKs
    lateinit var manager: PasswordManager

    @Test
    fun `isPasswordMatching - Return true if matching`() = runTest {
        val password = "test"
        val testSalt = byteArrayOf(1)
        val testHashedPassword = byteArrayOf(2)
        val entity = mockk<PasswordEntity> {
            every { salt } returns testSalt
            every { hashedPassword } returns testHashedPassword
        }

        coEvery {
            hashGenerator.hash(password, testSalt, PasswordHashGenerator.VERIFY)
        } returns testHashedPassword.copyOf()

        assertTrue(manager.isPasswordMatching(entity, password))
    }

    @Test
    fun `isPasswordMatching - Return false if not matching`() = runTest {
        val password = "test"
        val testSalt = byteArrayOf(1)
        val entity = mockk<PasswordEntity> {
            every { salt } returns testSalt
            every { hashedPassword } returns byteArrayOf(2)
        }

        coEvery { hashGenerator.hash(password, testSalt, PasswordHashGenerator.VERIFY) } returns byteArrayOf(3)

        assertFalse(manager.isPasswordMatching(entity, password))
    }

    private val user = User(id = UUID.randomUUID(), status = UserStatus.ENABLED, creationDate = now(), sessionId = null)

    private fun stored(id: UUID, hash: Byte, expirationDate: LocalDateTime? = null) = PasswordEntity(
        userId = user.id,
        salt = byteArrayOf(hash),
        hashedPassword = byteArrayOf(hash),
        creationDate = now(),
        expirationDate = expirationDate,
        sessionId = null
    ).apply { this.id = id }

    @Test
    fun `findMatchingPasswordIdOrNull - Return the password the end-user typed`() = runTest {
        val other = stored(UUID.randomUUID(), hash = 1)
        val matching = stored(UUID.randomUUID(), hash = 2)
        coEvery { passwordRepository.findByUserId(user.id) } returns listOf(other, matching)
        coEvery { hashGenerator.hash("typed", any(), PasswordHashGenerator.VERIFY) } returns byteArrayOf(2)

        assertEquals(matching.id, manager.findMatchingPasswordIdOrNull(user, "typed"))
    }

    @Test
    fun `findMatchingPasswordIdOrNull - Return null where only an expired password matches`() = runTest {
        val expired = stored(UUID.randomUUID(), hash = 2, expirationDate = now().minusDays(1))
        coEvery { passwordRepository.findByUserId(user.id) } returns listOf(expired)

        assertNull(manager.findMatchingPasswordIdOrNull(user, "typed"))
    }

    @Test
    fun `replacePasswords - Delete every other password, expired or not, before storing the new one`() = runTest {
        val replaced = stored(UUID.randomUUID(), hash = 1)
        val expired = stored(UUID.randomUUID(), hash = 2, expirationDate = now().minusDays(1))
        val newId = UUID.randomUUID()
        coEvery { passwordRepository.findByUserId(user.id) } returns listOf(replaced, expired)
        coEvery { passwordRepository.deleteByIdIn(listOf(replaced.id!!, expired.id!!)) } returns 2
        coEvery { passwordRepository.save(any()) } answers { firstArg<PasswordEntity>().apply { id = newId } }

        val replacement = manager.replacePasswords(user, HashedPassword(byteArrayOf(3), byteArrayOf(4)))

        assertEquals(newId, replacement.passwordId)
        assertEquals(listOf(replaced.id, expired.id), replacement.replacedPasswordIds)
        coVerifyOrder {
            objectLockRepository.lock(LockKey.User(user.id).stripe)
            passwordRepository.findByUserId(user.id)
            passwordRepository.deleteByIdIn(any())
            passwordRepository.save(match { it.hashedPassword.contentEquals(byteArrayOf(4)) && it.userId == user.id })
        }
    }

    @Test
    fun `isPasswordStored - Reads the passwords under the account lock`() = runTest {
        val kept = stored(UUID.randomUUID(), hash = 1)
        coEvery { passwordRepository.findByUserId(user.id) } returns listOf(kept)

        assertTrue(manager.isPasswordStored(user, kept.id!!))
        assertFalse(manager.isPasswordStored(user, UUID.randomUUID()))
        coVerifyOrder {
            objectLockRepository.lock(LockKey.User(user.id).stripe)
            passwordRepository.findByUserId(user.id)
        }
    }

    @Test
    fun `hasPassword - Counts an expired password only where asked to`() = runTest {
        coEvery { passwordRepository.findByUserId(user.id) } returns
            listOf(stored(UUID.randomUUID(), hash = 1, expirationDate = now().minusDays(1)))

        assertFalse(manager.hasPassword(user))
        assertTrue(manager.hasPassword(user, includesExpired = true))
    }
}
