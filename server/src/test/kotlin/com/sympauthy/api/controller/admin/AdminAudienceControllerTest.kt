package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminAudienceResourceMapper
import com.sympauthy.api.resource.admin.AdminAudienceResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.audience.Audience
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminAudienceControllerTest {

    @MockK
    lateinit var audienceManager: AudienceManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var audienceMapper: AdminAudienceResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminAudienceController

    private fun audience(id: String) = Audience(id = id, tokenAudience = id)

    private fun mockResource(audienceId: String) = AdminAudienceResource(
        audienceId = audienceId,
        tokenAudience = audienceId,
        signUpEnabled = true,
        invitationEnabled = false,
        clientsCount = 0
    )

    @Test
    fun `listAudiences - Order by identifier before slicing`() = runTest {
        val admin = audience("admin")

        // Handed last-first, the first page of one still holds the audience the order puts first.
        every { audienceManager.listAudiences() } returns listOf(audience("default"), admin)
        coEvery { clientManager.countClientsByAudienceId() } returns emptyMap()
        every { audienceMapper.toResource(admin, 0) } returns mockResource("admin")

        val result = controller.listAudiences(0, 1)

        assertEquals(listOf("admin"), result.audiences.map { it.audienceId })
        assertEquals(2, result.total)
    }
}
