package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class UserManagerTest {

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var userMapper: UserMapper

    @SpyK
    @InjectMockKs
    lateinit var manager: UserManager

    @Test
    fun `findByIdOrNull - Return user when found`() = runTest {
        val userId = UUID.randomUUID()
        val entity = mockk<UserEntity>()
        val user = mockk<User>()

        coEvery { userRepository.findById(userId) } returns entity
        every { userMapper.toUser(entity) } returns user

        val result = manager.findByIdOrNull(userId)

        assertSame(user, result)
    }

    @Test
    fun `findByIdOrNull - Return null when user not found`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { userRepository.findById(userId) } returns null

        val result = manager.findByIdOrNull(userId)

        assertNull(result)
    }

    @Test
    fun `findByIdOrNull - Return null when id is null`() = runTest {
        val result = manager.findByIdOrNull(null)

        assertNull(result)
    }

    @Test
    fun `findById - Return user when found`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockk<User>()

        coEvery { manager.findByIdOrNull(userId) } returns user

        val result = manager.findById(userId)

        assertSame(user, result)
    }

    @Test
    fun `findById - Throw exception when user not found`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { manager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<BusinessException> {
            manager.findById(userId)
        }

        assertEquals("user.not_found", exception.detailsId)
        assertEquals(userId.toString(), exception.values["userId"])
    }

    @Test
    fun `findById - Throw exception when id is null`() = runTest {
        coEvery { manager.findByIdOrNull(null) } returns null

        val exception = assertThrows<BusinessException> {
            manager.findById(null)
        }

        assertEquals("user.not_found", exception.detailsId)
        assertEquals("null", exception.values["userId"])
    }

    @Test
    fun `createUser - Create and return new user with ENABLED status`() = runTest {
        val entitySlot = slot<UserEntity>()
        val savedEntity = mockk<UserEntity>()
        val user = mockk<User>()

        coEvery { userRepository.save(capture(entitySlot)) } answers {
            savedEntity
        }
        every { userMapper.toUser(savedEntity) } returns user

        val result = manager.createUser()

        assertSame(user, result)
        assertEquals(UserStatus.ENABLED.name, entitySlot.captured.status)
        assertNotNull(entitySlot.captured.creationDate)
    }
}
