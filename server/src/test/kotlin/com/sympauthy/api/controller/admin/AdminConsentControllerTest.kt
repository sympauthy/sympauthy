package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminConsentResourceMapper
import com.sympauthy.api.resource.admin.AdminConsentResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.Consent
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
class AdminConsentControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var consentMapper: AdminConsentResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminConsentController

    private val userId: UUID = UUID.randomUUID()
    private val consentedAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

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

    private fun mockResource(consentId: UUID) = AdminConsentResource(
        consentId = consentId,
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
    fun `listConsents - Order by consent date, then by identifier`() = runTest {
        // Two of the three were granted in the same instant, which is what the identifier separates.
        val tiedFirst = consent(id(1), consentedAt)
        val tiedSecond = consent(id(2), consentedAt)
        val earlier = consent(id(3), consentedAt.minusDays(1))

        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { consentManager.findActiveConsentsByUser(userId) } returns
                listOf(tiedSecond, tiedFirst, earlier)
        listOf(tiedFirst, tiedSecond, earlier).forEach {
            every { consentMapper.toResource(it) } returns mockResource(it.id)
        }

        val result = controller.listConsents(userId, null, null)

        assertEquals(listOf(earlier.id, tiedFirst.id, tiedSecond.id), result.consents.map { it.consentId })
    }
}
