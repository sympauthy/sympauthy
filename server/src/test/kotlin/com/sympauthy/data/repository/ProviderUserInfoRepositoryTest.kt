package com.sympauthy.data.repository

import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import com.sympauthy.data.model.UserEntity
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

/**
 * H2-backed test of the three dates [ProviderUserInfoRepository] stores per link.
 *
 * None of what it proves is in the Kotlin: that the link date has a column at all under the name the
 * naming strategy derives, and that a full-entity update rewrites the two moving dates while carrying
 * the link date back unchanged. Both compile either way and fail against a real schema.
 */
@MicronautTest(
    environments = ["default", "test"],
    startApplication = false,
    transactional = false
)
class ProviderUserInfoRepositoryTest {

    @Inject
    lateinit var userInfoRepository: ProviderUserInfoRepository

    @Inject
    lateinit var userRepository: UserRepository

    private val providerId = "discord"
    private lateinit var userId: UUID

    private val linkDate: LocalDateTime = LocalDateTime.of(2026, 1, 15, 14, 30, 0)
    private val lastFetchedAt: LocalDateTime = LocalDateTime.of(2026, 3, 2, 9, 0, 0)

    @BeforeEach
    fun setUp() = runTest {
        userId = UserEntity(status = "enabled", creationDate = LocalDateTime.now())
            .also { userRepository.save(it) }
            .id!!
    }

    @AfterEach
    fun tearDown() = runTest {
        userInfoRepository.deleteByProviderIdAndUserId(providerId, userId)
        userRepository.deleteById(userId)
    }

    private suspend fun saveLink(): ProviderUserInfoEntity = userInfoRepository.save(
        ProviderUserInfoEntity(
            id = ProviderUserInfoEntityId(providerId = providerId, userId = userId),
            linkDate = linkDate,
            fetchDate = linkDate,
            changeDate = linkDate,
            subject = "123456789012345678"
        )
    )

    @Test
    fun `save - Persists the link date`() = runTest {
        saveLink()

        val stored = userInfoRepository.findByProviderIdAndUserId(providerId, userId)

        assertNotNull(stored)
        assertEquals(linkDate, stored!!.linkDate)
    }

    @Test
    fun `update - Carries the link date back unchanged while the fetch date moves`() = runTest {
        val stored = saveLink()

        userInfoRepository.update(
            ProviderUserInfoEntity(
                id = stored.id,
                linkDate = stored.linkDate,
                fetchDate = lastFetchedAt,
                changeDate = lastFetchedAt,
                subject = stored.subject
            )
        )

        val refreshed = userInfoRepository.findByProviderIdAndUserId(providerId, userId)

        assertNotNull(refreshed)
        assertEquals(linkDate, refreshed!!.linkDate)
        assertEquals(lastFetchedAt, refreshed.fetchDate)
    }
}
