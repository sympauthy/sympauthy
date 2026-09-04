package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminAudienceResourceMapper
import com.sympauthy.api.resource.admin.AdminAudienceResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.AudienceSearchManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.audience.AudienceWithClientCount
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminAudienceControllerTest {

    @MockK
    lateinit var audienceManager: AudienceManager

    @MockK
    lateinit var audienceSearchManager: AudienceSearchManager

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
    fun `listAudiences - Map every audience the page holds, with the count it carries`() = runTest {
        val admin = audience("admin")
        val resource = mockResource("admin")

        coEvery { audienceSearchManager.listAudiences(PageParams(0, 20)) } returns Page(
            items = listOf(AudienceWithClientCount(admin, 4)),
            page = 3,
            size = 7,
            total = 42
        )
        every { audienceMapper.toResource(admin, 4) } returns resource

        val result = controller.listAudiences(null, null)

        assertSame(resource, result.audiences.single())
        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}
