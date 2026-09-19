package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminConsentResourceMapper
import com.sympauthy.api.resource.admin.AdminConsentResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.api.util.noCapabilities
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.collection.ConsentCollectionManager
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
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
import org.junit.jupiter.api.Assertions.*
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
    lateinit var consentCollectionManager: ConsentCollectionManager

    @MockK
    lateinit var consentMapper: AdminConsentResourceMapper

    @MockK
    lateinit var capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper

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
    fun `listConsents - Map every consent the page holds, and publish the page it came in`() = runTest {
        val consent = consent(id(1), consentedAt)
        val resource = mockResource(consent.id)

        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { consentCollectionManager.capabilities() } returns noCapabilities()
        coEvery { consentCollectionManager.listUserConsents(userId, any(), PageParams(0, 20)) } returns Page(
            items = listOf(consent),
            page = 3,
            size = 7,
            total = 42
        )
        every { consentMapper.toResource(consent) } returns resource

        val result = controller.listConsents(collectionRequest(), userId, null, null, null, null)

        assertSame(resource, result.consents.single())
        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}
