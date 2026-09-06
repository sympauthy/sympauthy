package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminClientResourceMapper
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.api.resource.admin.AdminClientSummaryResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ClientSearchManager
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.client.Client
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminClientControllerTest {

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var clientSearchManager: ClientSearchManager

    @MockK
    lateinit var clientMapper: AdminClientResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminClientController

    private fun mockSummaryResource(clientId: String): AdminClientSummaryResource = AdminClientSummaryResource(
        clientId = clientId,
        type = "confidential",
        audienceId = "default",
        allowedRedirectUris = emptyList()
    )

    private fun mockResource(clientId: String): AdminClientResource = AdminClientResource(
        clientId = clientId,
        type = "confidential",
        audienceId = "default",
        allowedGrantTypes = listOf("authorization_code"),
        authorizationFlowId = null,
        allowedScopes = emptyList(),
        defaultScopes = emptyList(),
        allowedRedirectUris = emptyList(),
        authorizationWebhook = null,
        accessReviewWebhook = null
    )

    @Test
    fun `listClients - Map every client the page holds, and publish the page it came in`() = runTest {
        val client = mockk<Client>()
        val resource = mockSummaryResource("c1")

        coEvery { clientSearchManager.listClients(PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)) } returns Page(
            items = listOf(client),
            page = 3,
            size = 7,
            total = 42
        )
        every { clientMapper.toSummaryResource(client) } returns resource

        val result = controller.listClients(null, null)

        assertSame(resource, result.clients.single())
        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }

    @Test
    fun `getClient - Return client when found`() = runTest {
        val client = mockk<Client>()
        val resource = mockResource("my-app")

        coEvery { clientManager.findClientByIdOrNull("my-app") } returns client
        every { clientMapper.toResource(client) } returns resource

        val result = controller.getClient("my-app")

        assertSame(resource, result)
    }

    @Test
    fun `getClient - Throw 404 when client not found`() = runTest {
        coEvery { clientManager.findClientByIdOrNull("unknown") } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.getClient("unknown")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}
