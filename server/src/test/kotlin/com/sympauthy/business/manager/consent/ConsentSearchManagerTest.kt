package com.sympauthy.business.manager.consent

import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.page.PageParams
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ConsentSearchManagerTest {

    @MockK
    lateinit var consentManager: ConsentManager

    @InjectMockKs
    lateinit var consentSearchManager: ConsentSearchManager

    private val userId: UUID = UUID.randomUUID()
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

    @Test
    fun `listUserConsents - Order by consent date, then by identifier`() = runTest {
        // Two of the three were granted in the same instant, which is what the identifier separates.
        val tiedFirst = consent(id(1), consentedAt)
        val tiedSecond = consent(id(2), consentedAt)
        val earlier = consent(id(3), consentedAt.minusDays(1))
        coEvery { consentManager.findActiveConsentsByUser(userId) } returns
                listOf(tiedSecond, tiedFirst, earlier)

        val result = consentSearchManager.listUserConsents(userId, firstPage)

        assertEquals(listOf(earlier.id, tiedFirst.id, tiedSecond.id), result.items.map { it.id })
    }

    @Test
    fun `listUserConsents - Return the page the parameters name`() = runTest {
        coEvery { consentManager.findActiveConsentsByUser(userId) } returns listOf(
            consent(id(1), consentedAt),
            consent(id(2), consentedAt.plusDays(1)),
            consent(id(3), consentedAt.plusDays(2))
        )

        val result = consentSearchManager.listUserConsents(userId, PageParams(page = 1, size = 2))

        assertEquals(listOf(id(3)), result.items.map { it.id })
        assertEquals(3, result.total)
    }
}
