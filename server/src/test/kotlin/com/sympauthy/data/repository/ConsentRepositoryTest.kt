package com.sympauthy.data.repository

import com.sympauthy.data.model.ConsentEntity
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import com.sympauthy.data.model.UserEntity
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

/**
 * H2-backed test of the paging and counting queries [ConsentRepository] declares as raw SQL.
 *
 * None of what it proves is in the Kotlin. That the composite order is total — two consents sharing a
 * `consented_at` are separated by the primary key, so pages of one walk every row exactly once —
 * compiles either way and fails only against a database. So does the optional subject: an absent one is
 * bound as a null the driver has to type, and the `COALESCE` is what gives it a single typed usage.
 */
@MicronautTest(
    environments = ["default", "test", "h2"],
    startApplication = false,
    transactional = false
)
class ConsentRepositoryTest {

    @Inject
    lateinit var consentRepository: ConsentRepository

    @Inject
    lateinit var userInfoRepository: ProviderUserInfoRepository

    @Inject
    lateinit var userRepository: UserRepository

    /**
     * The audience the consents carry, and the two providers their users are linked under. Each names
     * this test class, so no other class's rows fall inside the queries below.
     */
    private val audienceId = "consent-repository-test"
    private val providerId = "consent-repository-test-provider"
    private val otherProviderId = "consent-repository-test-other-provider"

    private val baseDate: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

    private val userIds = mutableListOf<UUID>()
    private val consentIds = mutableListOf<UUID>()
    private val linkedUserIds = mutableListOf<Pair<String, UUID>>()

    /**
     * Five users, of which the first has no provider link at all, the next two are linked to [providerId]
     * under distinct subjects, and the fourth is linked to another provider. Their consents are ordered by
     * date except for the third and fourth, which share one so the identifier tiebreak is exercised, and the
     * fifth is revoked.
     */
    @BeforeEach
    fun setUp() = runTest {
        repeat(5) {
            UserEntity(status = "enabled", creationDate = baseDate)
                .also { user -> userRepository.save(user) }
                .id!!
                .also(userIds::add)
        }

        saveConsent(userIds[0], baseDate)
        saveConsent(userIds[1], baseDate.plusMinutes(1))
        saveConsent(userIds[2], baseDate.plusMinutes(2))
        saveConsent(userIds[3], baseDate.plusMinutes(2))
        saveConsent(userIds[4], baseDate.plusMinutes(3), revokedAt = baseDate.plusMinutes(4))

        saveLink(providerId, userIds[1], "subject-1")
        saveLink(providerId, userIds[2], "subject-2")
        saveLink(otherProviderId, userIds[3], "subject-3")
        saveLink(providerId, userIds[4], "subject-4")
    }

    @AfterEach
    fun tearDown() = runTest {
        consentIds.forEach { consentRepository.deleteById(it) }
        linkedUserIds.forEach { (providerId, userId) ->
            userInfoRepository.deleteByProviderIdAndUserId(providerId, userId)
        }
        userIds.forEach { userRepository.deleteById(it) }
        consentIds.clear()
        linkedUserIds.clear()
        userIds.clear()
    }

    private suspend fun saveConsent(
        userId: UUID,
        consentedAt: LocalDateTime,
        revokedAt: LocalDateTime? = null
    ): UUID = ConsentEntity(
        userId = userId,
        audienceId = audienceId,
        promptedByClientId = "consent-repository-test-client",
        scopes = arrayOf("profile"),
        consentedAt = consentedAt,
        revokedAt = revokedAt
    ).let { consentRepository.save(it).id!! }
        .also { consentIds.add(it) }

    private suspend fun saveLink(providerId: String, userId: UUID, subject: String) {
        userInfoRepository.save(
            ProviderUserInfoEntity(
                id = ProviderUserInfoEntityId(providerId = providerId, userId = userId),
                linkDate = baseDate,
                fetchDate = baseDate,
                changeDate = baseDate,
                subject = subject
            )
        )
        linkedUserIds.add(providerId to userId)
    }

    private suspend fun readWholeAudience(): List<UUID> = consentRepository
        .findActiveByAudienceId(audienceId, limit = 10, offset = 0)
        .map { it.id!! }

    @Test
    fun `findActiveByAudienceId - Orders by consent date`() = runTest {
        val page = readWholeAudience()

        assertEquals(consentIds.take(2), page.take(2))
        assertEquals(consentIds.subList(2, 4).toSet(), page.drop(2).toSet())
    }

    /**
     * The tiebreak is asserted as determinism rather than as a direction: the two dialects collate a `uuid`
     * as unsigned bytes and Kotlin compares it signed, so naming which of the tied pair comes first would
     * pin a collation this query does not care about. What it needs is that the pair does not swap.
     */
    @Test
    fun `findActiveByAudienceId - Breaks a tied consent date on the identifier, so two reads agree`() = runTest {
        assertEquals(readWholeAudience(), readWholeAudience())
    }

    @Test
    fun `findActiveByAudienceId - Walking pages of one yields every consent exactly once`() = runTest {
        val walked = (0 until 4).flatMap { offset ->
            consentRepository.findActiveByAudienceId(audienceId, limit = 1, offset = offset)
                .map { it.id!! }
        }

        assertEquals(readWholeAudience(), walked)
    }

    @Test
    fun `findActiveByAudienceId - Excludes revoked consents`() = runTest {
        val page = consentRepository.findActiveByAudienceId(audienceId, limit = 10, offset = 0)

        assertFalse(page.any { it.userId == userIds[4] })
    }

    @Test
    fun `findActiveByAudienceId - Returns nothing past the last page`() = runTest {
        val page = consentRepository.findActiveByAudienceId(audienceId, limit = 2, offset = 4)

        assertEquals(emptyList<UUID>(), page.map { it.id!! })
    }

    @Test
    fun `countActiveByAudienceId - Counts the rows the pages walk`() = runTest {
        val count = consentRepository.countActiveByAudienceId(audienceId)

        assertEquals(readWholeAudience().size.toLong(), count)
    }

    @Test
    fun `findActiveByAudienceIdAndProvider - Keeps only the users linked to the provider`() = runTest {
        val page = consentRepository.findActiveByAudienceIdAndProvider(
            audienceId = audienceId,
            providerId = providerId,
            subject = null,
            limit = 10,
            offset = 0
        )

        assertEquals(listOf(userIds[1], userIds[2]), page.map { it.userId })
    }

    @Test
    fun `findActiveByAudienceIdAndProvider - Narrows to the account bearing the subject`() = runTest {
        val page = consentRepository.findActiveByAudienceIdAndProvider(
            audienceId = audienceId,
            providerId = providerId,
            subject = "subject-2",
            limit = 10,
            offset = 0
        )

        assertEquals(listOf(userIds[2]), page.map { it.userId })
    }

    @Test
    fun `findActiveByAudienceIdAndProvider - Pages the filtered rows in the same order`() = runTest {
        val walked = (0 until 2).flatMap { offset ->
            consentRepository.findActiveByAudienceIdAndProvider(
                audienceId = audienceId,
                providerId = providerId,
                subject = null,
                limit = 1,
                offset = offset
            ).map { it.userId }
        }

        assertEquals(listOf(userIds[1], userIds[2]), walked)
    }

    @Test
    fun `countActiveByAudienceIdAndProvider - Counts the rows the filtered pages walk`() = runTest {
        val withoutSubject = consentRepository.countActiveByAudienceIdAndProvider(
            audienceId = audienceId,
            providerId = providerId,
            subject = null
        )
        val withSubject = consentRepository.countActiveByAudienceIdAndProvider(
            audienceId = audienceId,
            providerId = providerId,
            subject = "subject-2"
        )

        assertEquals(2L, withoutSubject)
        assertEquals(1L, withSubject)
    }
}
