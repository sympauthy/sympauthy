package com.sympauthy.business.manager.collection

import com.sympauthy.api.util.criteriaOf
import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.ClientReference
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.User
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ConsentCollectionManagerTest {

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var audienceManager: AudienceManager

    @MockK
    lateinit var clientManager: ClientManager

    @InjectMockKs
    lateinit var consentCollectionManager: ConsentCollectionManager

    private val userId: UUID = UUID.randomUUID()

    private val user = mockk<User> { every { id } returns userId }
    private val consentedAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)
    private val firstPage = PageParams(page = 0, size = 20)

    private fun id(last: Int): UUID = UUID.fromString("00000000-0000-0000-0000-00000000000$last")

    private fun consent(id: UUID, consentedAt: LocalDateTime) = Consent(
        id = id,
        userId = userId,
        audienceId = "default",
        promptedByClientId = "client",
        scopes = listOf("openid"),
        consentedAt = consentedAt,
        revokedAt = null,
        revokedBy = null,
        revokedById = null
    )

    private fun knownClientReferences() {
        coEvery { clientManager.getClientReferences(any()) } answers {
            firstArg<Collection<String>>().associateWith { ClientReference(it, "Web app") }
        }
    }

    @Test
    fun `listUserConsents - Order by consent date, then by identifier`() = runTest {
        // Two of the three were granted in the same instant, which is what the identifier separates.
        val tiedFirst = consent(id(1), consentedAt)
        val tiedSecond = consent(id(2), consentedAt)
        val earlier = consent(id(3), consentedAt.minusDays(1))
        knownClientReferences()
        coEvery { consentManager.findActiveConsentsByUser(userId) } returns
                listOf(tiedSecond, tiedFirst, earlier)

        val result = consentCollectionManager.listUserConsents(user, criteriaOf(), firstPage)

        assertEquals(listOf(earlier.id, tiedFirst.id, tiedSecond.id), result.items.map { it.consent.id })
    }

    @Test
    fun `listUserConsents - Return the page the parameters name`() = runTest {
        knownClientReferences()
        coEvery { consentManager.findActiveConsentsByUser(userId) } returns listOf(
            consent(id(1), consentedAt),
            consent(id(2), consentedAt.plusDays(1)),
            consent(id(3), consentedAt.plusDays(2))
        )

        val result = consentCollectionManager.listUserConsents(user, criteriaOf(), PageParams(page = 1, size = 2))

        assertEquals(listOf(id(3)), result.items.map { it.consent.id })
        assertEquals(3, result.total)
    }

    @Test
    fun `listUserConsents - Keep the consents covering the scope a criterion names`() = runTest {
        val openId = consent(id(1), consentedAt)
        val profile = consent(id(2), consentedAt).copy(scopes = listOf("profile"))
        knownClientReferences()
        coEvery { consentManager.findActiveConsentsByUser(userId) } returns listOf(openId, profile)

        val result = consentCollectionManager.listUserConsents(user, criteriaOf("scope" to "profile"), firstPage)

        assertEquals(listOf(profile.id), result.items.map { it.consent.id })
    }

    @Test
    fun `listUserConsents - Name the client whose authorization request last updated each consent`() = runTest {
        knownClientReferences()
        coEvery { consentManager.findActiveConsentsByUser(userId) } returns listOf(consent(id(1), consentedAt))

        val result = consentCollectionManager.listUserConsents(user, criteriaOf(), firstPage)

        assertEquals(ClientReference("client", "Web app"), result.items.single().promptedByClient)
    }

    @Test
    fun `listUserConsents - Keep the consents a client the configuration no longer declares prompted`() = runTest {
        val legacy = consent(id(1), consentedAt).copy(promptedByClientId = "legacy")
        knownClientReferences()
        coEvery { consentManager.findActiveConsentsByUser(userId) } returns
                listOf(legacy, consent(id(2), consentedAt))

        val result = consentCollectionManager.listUserConsents(
            user,
            criteriaOf("prompted_by_client" to "legacy"),
            firstPage
        )

        assertEquals(listOf(legacy.id), result.items.map { it.consent.id })
    }

    @Test
    fun `capabilities - Filter on the clients that prompted the active consents, beside the declared ones`() =
        runTest {
            criteriaOf()

            val client = consentCollectionManager.capabilities().fieldOrNull("prompted_by_client")

            assertEquals(listOf("client", "legacy"), client?.values?.map { it.value })
            assertEquals(listOf("Web app", "legacy"), client?.values?.map { it.defaultName })
        }

    private suspend fun criteriaOf(
        vararg filters: Pair<String, String>,
        sort: String? = null,
        query: String? = null
    ): com.sympauthy.business.model.collection.CollectionCriteria {
        every { audienceManager.listAudiences() } returns listOf(Audience(id = "default", tokenAudience = "default"))
        coEvery { consentManager.listPromptedByClientIds() } returns listOf("client", "legacy")
        coEvery { clientManager.getClientFieldValues(listOf("client", "legacy")) } returns listOf(
            CollectionFieldValue("client", "fields.client_id.client", "Web app"),
            CollectionFieldValue("legacy", "fields.client_id.legacy")
        )
        return consentCollectionManager.capabilities().criteriaOf(*filters, sort = sort, query = query)
    }
}
