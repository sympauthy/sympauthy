package com.sympauthy.data.repository

import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.UserEntity
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.called
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

@MicronautTest(
    environments = ["default", "test"],
    startApplication = false,
    transactional = false
)
class CollectedClaimRepositoryTest {

    @Inject
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var claimValueMapper: ClaimValueMapper

    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID
    private lateinit var user3Id: UUID

    @BeforeEach
    fun setUp() = runTest {
        collectedClaimRepository.deleteAll()
        userRepository.deleteAll()

        val now = LocalDateTime.now()
        val user1 = UserEntity(status = "enabled", creationDate = now).also { userRepository.save(it) }
        val user2 = UserEntity(status = "enabled", creationDate = now).also { userRepository.save(it) }
        val user3 = UserEntity(status = "enabled", creationDate = now).also { userRepository.save(it) }

        user1Id = user1.id!!
        user2Id = user2.id!!
        user3Id = user3.id!!

        // user1: email=alice@test.com, name=Alice
        collectedClaimRepository.save(
            CollectedClaimEntity(
                userId = user1Id,
                claim = "email",
                value = claimValueMapper.toEntity("alice@test.com"),
                verified = true,
                collectionDate = now,
                verificationDate = now
            )
        )
        collectedClaimRepository.save(
            CollectedClaimEntity(
                userId = user1Id,
                claim = "name",
                value = claimValueMapper.toEntity("Alice"),
                verified = null,
                collectionDate = now,
                verificationDate = null
            )
        )

        // user2: email=bob@test.com, name=Bob
        collectedClaimRepository.save(
            CollectedClaimEntity(
                userId = user2Id,
                claim = "email",
                value = claimValueMapper.toEntity("bob@test.com"),
                verified = true,
                collectionDate = now,
                verificationDate = now
            )
        )
        collectedClaimRepository.save(
            CollectedClaimEntity(
                userId = user2Id,
                claim = "name",
                value = claimValueMapper.toEntity("Bob"),
                verified = null,
                collectionDate = now,
                verificationDate = null
            )
        )

        // user3: email=alice@test.com (same email as user1), name=Charlie
        collectedClaimRepository.save(
            CollectedClaimEntity(
                userId = user3Id,
                claim = "email",
                value = claimValueMapper.toEntity("alice@test.com"),
                verified = true,
                collectionDate = now,
                verificationDate = now
            )
        )
        collectedClaimRepository.save(
            CollectedClaimEntity(
                userId = user3Id,
                claim = "name",
                value = claimValueMapper.toEntity("Charlie"),
                verified = null,
                collectionDate = now,
                verificationDate = null
            )
        )
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns empty list when claimValues is empty`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns users matching a single claim`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf("email" to claimValueMapper.toEntity("alice@test.com"))
        )
        assertEquals(setOf(user1Id, user3Id), result.toSet())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns only users matching ALL claims`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf(
                "email" to claimValueMapper.toEntity("alice@test.com"),
                "name" to claimValueMapper.toEntity("Alice")
            )
        )
        assertEquals(listOf(user1Id), result)
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns empty when no user matches all claims`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf(
                "email" to claimValueMapper.toEntity("alice@test.com"),
                "name" to claimValueMapper.toEntity("Bob"),
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns empty when claim value does not exist`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf("email" to claimValueMapper.toEntity("nonexistent@test.com"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns empty when claim id does not exist`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf("phone" to claimValueMapper.toEntity("123456"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns single user matching by name`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf("name" to claimValueMapper.toEntity("Bob"))
        )
        assertEquals(listOf(user2Id), result)
    }
}
