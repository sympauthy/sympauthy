package com.sympauthy.business.manager.password

import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.data.model.PasswordEntity
import com.sympauthy.data.repository.PasswordRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

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

        coEvery { hashGenerator.hash(password, testSalt) } returns testHashedPassword.copyOf()

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

        coEvery { hashGenerator.hash(password, testSalt) } returns byteArrayOf(3)

        assertFalse(manager.isPasswordMatching(entity, password))
    }
}
