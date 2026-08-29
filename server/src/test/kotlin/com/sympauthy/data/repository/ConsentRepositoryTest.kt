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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

/**
 * Covers the query-annotated methods: their order, their page boundaries, and that each count answers
 * for exactly the rows its page query returns.
 *
 * None of this is visible in the Kotlin — the order, the coalesced subject and the `EXISTS` are a SQL
 * string sent to the database — so it is exercised against a real one.
 */
@MicronautTest(
    environments = ["default", "test"],
    startApplication = false,
    transactional = false
)
class ConsentRepositoryTest {

    @Inject
    lateinit var consentRepository: ConsentRepository

    @Inject
    lateinit var providerUserInfoRepository: ProviderUserInfoRepository

    @Inject
    lateinit var userRepository: UserRepository

    /** The first four users hold an active consent for [AUDIENCE_ID], oldest consent first. */
    private lateinit var userIds: List<UUID>

    /**
     * The consent of [userIds] index 1 and index 2, which share a consent date. Their relative order is
     * therefore decided by the tiebreak alone.
     */
    private lateinit var tiedConsentIds: List<UUID>

    private val createdConsentIds = mutableListOf<UUID>()
    private val createdProviderLinks = mutableListOf<ProviderUserInfoEntityId>()

    // This @MicronautTest shares its H2 database with the other @MicronautTest classes, so we must not
    // deleteAll() — other tests' rows reference these tables via foreign keys. Both audiences below are
    // named for this class so that no other test's consents can fall inside a query under test, and
    // tearDown removes only the rows created here.
    @BeforeEach
    fun setUp() = runTest {
        userIds = (1..6).map {
            userRepository.save(UserEntity(status = "enabled", creationDate = CONSENTED_AT)).id!!
        }

        // Indices 1 and 2 share a consent date, so only the tiebreak separates them.
        val consentIds = listOf(
            saveConsent(userIds[0], AUDIENCE_ID, CONSENTED_AT),
            saveConsent(userIds[1], AUDIENCE_ID, CONSENTED_AT.plusHours(1)),
            saveConsent(userIds[2], AUDIENCE_ID, CONSENTED_AT.plusHours(1)),
            saveConsent(userIds[3], AUDIENCE_ID, CONSENTED_AT.plusHours(2))
        )
        tiedConsentIds = listOf(consentIds[1], consentIds[2])

        // Revoked, and so excluded however early its consent date is.
        saveConsent(userIds[4], AUDIENCE_ID, CONSENTED_AT.minusDays(1), revokedAt = CONSENTED_AT)
        // Another audience's consent, and so none of this audience's business.
        saveConsent(userIds[5], OTHER_AUDIENCE_ID, CONSENTED_AT)

        // The first two users are linked to the provider; the second one shares its subject with a link
        // held by a user of the other audience, so a subject alone cannot select a user.
        saveProviderLink(userIds[0], PROVIDER_ID, "subject-0")
        saveProviderLink(userIds[1], PROVIDER_ID, SHARED_SUBJECT)
        saveProviderLink(userIds[5], PROVIDER_ID, SHARED_SUBJECT)
        // A link to another provider, which the provider filter must not match.
        saveProviderLink(userIds[3], OTHER_PROVIDER_ID, "subject-3")
    }

    @AfterEach
    fun tearDown() = runTest {
        createdProviderLinks.forEach {
            providerUserInfoRepository.deleteByProviderIdAndUserId(it.providerId, it.userId)
        }
        createdProviderLinks.clear()
        createdConsentIds.forEach { consentRepository.deleteById(it) }
        createdConsentIds.clear()
        userIds.forEach { userRepository.deleteById(it) }
    }

    @Test
    fun `findActiveByAudienceId - Order the consents by consent date`() = runTest {
        val consents = consentRepository.findActiveByAudienceId(AUDIENCE_ID, limit = 10, offset = 0)

        assertEquals(
            listOf(CONSENTED_AT, CONSENTED_AT.plusHours(1), CONSENTED_AT.plusHours(1), CONSENTED_AT.plusHours(2)),
            consents.map(ConsentEntity::consentedAt)
        )
    }

    @Test
    fun `findActiveByAudienceId - Break a tie on the consent date by identifier`() = runTest {
        val consents = consentRepository.findActiveByAudienceId(AUDIENCE_ID, limit = 10, offset = 0)

        // This states the contract rather than guarding it: the index the audience is read through
        // carries the identifier as its last column, so H2 answers in this order even when the query
        // does not ask for it. Dropping the tiebreak still passes here, and stops being safe elsewhere.
        // Both dialects also collate a uuid as unsigned bytes, which is not what UUID.compareTo gives.
        assertEquals(
            tiedConsentIds.sortedWith(UNSIGNED_UUID_ORDER),
            consents.map { it.id!! }.subList(1, 3)
        )
    }

    @Test
    fun `findActiveByAudienceId - Exclude the revoked consents and the other audiences`() = runTest {
        val consents = consentRepository.findActiveByAudienceId(AUDIENCE_ID, limit = 10, offset = 0)

        assertEquals(userIds.take(4).toSet(), consents.map(ConsentEntity::userId).toSet())
    }

    @Test
    fun `findActiveByAudienceId - Split the consents into pages that neither overlap nor skip`() = runTest {
        val pages = (0..1).map { page ->
            consentRepository.findActiveByAudienceId(AUDIENCE_ID, limit = 2, offset = page * 2)
        }

        assertEquals(
            consentRepository.findActiveByAudienceId(AUDIENCE_ID, limit = 10, offset = 0).map { it.id!! },
            pages.flatten().map { it.id!! }
        )
    }

    @Test
    fun `findActiveByAudienceId - Return nothing past the last page`() = runTest {
        val consents = consentRepository.findActiveByAudienceId(AUDIENCE_ID, limit = 2, offset = 4)

        assertTrue(consents.isEmpty())
    }

    @Test
    fun `countActiveByAudienceId - Count the consents the page query returns`() = runTest {
        val count = consentRepository.countActiveByAudienceId(AUDIENCE_ID)

        assertEquals(4L, count)
    }

    @Test
    fun `findActiveByAudienceIdAndProvider - Keep only the users linked to the provider`() = runTest {
        val consents = consentRepository.findActiveByAudienceIdAndProvider(
            AUDIENCE_ID, PROVIDER_ID, subject = null, limit = 10, offset = 0
        )

        assertEquals(userIds.take(2), consents.map(ConsentEntity::userId))
    }

    @Test
    fun `findActiveByAudienceIdAndProvider - Keep only the user carrying the subject`() = runTest {
        val consents = consentRepository.findActiveByAudienceIdAndProvider(
            AUDIENCE_ID, PROVIDER_ID, SHARED_SUBJECT, limit = 10, offset = 0
        )

        // The other audience's user holds the same subject and stays out of the audience's page.
        assertEquals(listOf(userIds[1]), consents.map(ConsentEntity::userId))
    }

    @Test
    fun `findActiveByAudienceIdAndProvider - Return nothing for an unlinked provider`() = runTest {
        val consents = consentRepository.findActiveByAudienceIdAndProvider(
            AUDIENCE_ID, "unlinked-provider", subject = null, limit = 10, offset = 0
        )

        assertTrue(consents.isEmpty())
    }

    @Test
    fun `findActiveByAudienceIdAndProvider - Page the filtered consents`() = runTest {
        val consents = consentRepository.findActiveByAudienceIdAndProvider(
            AUDIENCE_ID, PROVIDER_ID, subject = null, limit = 1, offset = 1
        )

        assertEquals(listOf(userIds[1]), consents.map(ConsentEntity::userId))
    }

    @Test
    fun `countActiveByAudienceIdAndProvider - Count every subject when none is given`() = runTest {
        val count = consentRepository.countActiveByAudienceIdAndProvider(AUDIENCE_ID, PROVIDER_ID, subject = null)

        assertEquals(2L, count)
    }

    @Test
    fun `countActiveByAudienceIdAndProvider - Count only the given subject`() = runTest {
        val count = consentRepository.countActiveByAudienceIdAndProvider(AUDIENCE_ID, PROVIDER_ID, SHARED_SUBJECT)

        assertEquals(1L, count)
    }

    private suspend fun saveConsent(
        userId: UUID,
        audienceId: String,
        consentedAt: LocalDateTime,
        revokedAt: LocalDateTime? = null
    ): UUID {
        val id = consentRepository.save(
            ConsentEntity(
                userId = userId,
                audienceId = audienceId,
                promptedByClientId = "client",
                scopes = arrayOf("profile"),
                consentedAt = consentedAt,
                revokedAt = revokedAt
            )
        ).id!!
        createdConsentIds += id
        return id
    }

    private suspend fun saveProviderLink(userId: UUID, providerId: String, subject: String) {
        val id = ProviderUserInfoEntityId(providerId = providerId, userId = userId)
        providerUserInfoRepository.save(
            ProviderUserInfoEntity(
                id = id,
                fetchDate = CONSENTED_AT,
                changeDate = CONSENTED_AT,
                subject = subject
            )
        )
        createdProviderLinks += id
    }

    private companion object {
        val CONSENTED_AT: LocalDateTime = LocalDateTime.of(2024, 1, 1, 0, 0)
        const val AUDIENCE_ID = "consent-repository-test"
        const val OTHER_AUDIENCE_ID = "consent-repository-test-other"
        const val PROVIDER_ID = "consent-repository-test-provider"
        const val OTHER_PROVIDER_ID = "consent-repository-test-other-provider"
        const val SHARED_SUBJECT = "shared-subject"

        val UNSIGNED_UUID_ORDER: Comparator<UUID> = compareBy<UUID> { it.mostSignificantBits.toULong() }
            .thenBy { it.leastSignificantBits.toULong() }
    }
}
