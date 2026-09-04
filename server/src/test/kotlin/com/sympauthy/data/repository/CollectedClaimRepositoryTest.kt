package com.sympauthy.data.repository

import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.UserEntity
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

/**
 * H2-backed test of [findUserIdsMatchingAllClaims], the criteria query no unit test can reach.
 *
 * That a user matches only when every claim in the map matches is what the query has to prove, and it
 * compiles either way.
 */
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

    /**
     * Every claim value this class writes and queries by carries the test class's own name.
     *
     * This @MicronautTest shares its H2 database with the other @MicronautTest classes, so it must not
     * deleteAll(): tearDown removes only the rows setUp created, and a value no other class writes is
     * what keeps the assertions below exact against rows those classes leave behind.
     */
    private val qualifier = "collected-claim-repository-test"
    private val aliceEmail = "alice@$qualifier.test"
    private val bobEmail = "bob@$qualifier.test"
    private val aliceName = "Alice-$qualifier"
    private val bobName = "Bob-$qualifier"
    private val charlieName = "Charlie-$qualifier"

    private val userIds = mutableListOf<UUID>()
    private val claimIds = mutableListOf<UUID>()

    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID
    private lateinit var user3Id: UUID

    /**
     * Three users, of which the first and the third share an email and differ by name, so that a query
     * over both claims separates them and a query over the email alone does not.
     */
    @BeforeEach
    fun setUp() = runTest {
        val now = LocalDateTime.now()

        user1Id = saveUser(now)
        user2Id = saveUser(now)
        user3Id = saveUser(now)

        saveClaim(user1Id, "email", aliceEmail, now)
        saveClaim(user1Id, "name", aliceName, now)

        saveClaim(user2Id, "email", bobEmail, now)
        saveClaim(user2Id, "name", bobName, now)

        saveClaim(user3Id, "email", aliceEmail, now)
        saveClaim(user3Id, "name", charlieName, now)
    }

    /**
     * Removes whatever setUp managed to write, including when it failed part-way: a claim left behind
     * here is a row another class's query can match, which is a failure with nothing in its own file to
     * explain it.
     */
    @AfterEach
    fun tearDown() = runTest {
        claimIds.forEach { collectedClaimRepository.deleteById(it) }
        userIds.forEach { userRepository.deleteById(it) }
        claimIds.clear()
        userIds.clear()
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns empty list when claimValues is empty`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns users matching a single claim`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf("email" to claimValueMapper.toEntity(aliceEmail))
        )
        assertEquals(setOf(user1Id, user3Id), result.toSet())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns only users matching ALL claims`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf(
                "email" to claimValueMapper.toEntity(aliceEmail),
                "name" to claimValueMapper.toEntity(aliceName)
            )
        )
        assertEquals(listOf(user1Id), result)
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns empty when no user matches all claims`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf(
                "email" to claimValueMapper.toEntity(aliceEmail),
                "name" to claimValueMapper.toEntity(bobName),
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns empty when claim value does not exist`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf("email" to claimValueMapper.toEntity("nonexistent@$qualifier.test"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns empty when claim id does not exist`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf("phone" to claimValueMapper.toEntity("phone-$qualifier"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUserIdsMatchingAllClaims - returns single user matching by name`() = runTest {
        val result = collectedClaimRepository.findUserIdsMatchingAllClaims(
            mapOf("name" to claimValueMapper.toEntity(bobName))
        )
        assertEquals(listOf(user2Id), result)
    }

    private suspend fun saveUser(now: LocalDateTime): UUID = UserEntity(status = "enabled", creationDate = now)
        .let { userRepository.save(it).id!! }
        .also(userIds::add)

    private suspend fun saveClaim(userId: UUID, claim: String, value: String, now: LocalDateTime) {
        CollectedClaimEntity(
            userId = userId,
            claim = claim,
            value = claimValueMapper.toEntity(value),
            verified = if (claim == "email") true else null,
            collectionDate = now,
            verificationDate = if (claim == "email") now else null
        ).let { collectedClaimRepository.save(it).id!! }
            .also(claimIds::add)
    }
}
