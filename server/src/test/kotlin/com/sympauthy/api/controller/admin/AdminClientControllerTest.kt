package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminClientResourceMapper
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.api.resource.admin.AdminClientSummaryResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.DEFAULT_PAGE_SIZE
import com.sympauthy.business.manager.ClientManager
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
    lateinit var clientMapper: AdminClientResourceMapper

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
        authorizationWebhook = null
    )

    @Test
    fun `listClients - Return paginated list with defaults`() = runTest {
        val client1 = mockk<Client>()
        val client2 = mockk<Client>()
        val resource1 = mockSummaryResource("c1")
        val resource2 = mockSummaryResource("c2")

        coEvery { clientManager.listClients() } returns listOf(client1, client2)
        every { clientMapper.toSummaryResource(client1) } returns resource1
        every { clientMapper.toSummaryResource(client2) } returns resource2

        val result = controller.listClients(null, null)

        assertEquals(DEFAULT_PAGE, result.page)
        assertEquals(DEFAULT_PAGE_SIZE, result.size)
        assertEquals(2, result.total)
        assertEquals(2, result.clients.size)
        assertSame(resource1, result.clients[0])
        assertSame(resource2, result.clients[1])
    }

    @Test
    fun `listClients - Apply page and size`() = runTest {
        val clients = (1..5).map { mockk<Client>() }
        val resources = (1..5).map { mockSummaryResource("c$it") }

        coEvery { clientManager.listClients() } returns clients
        // The second page of two holds the third and fourth client.
        listOf(2, 3).forEach { i ->
            every { clientMapper.toSummaryResource(clients[i]) } returns resources[i]
        }

        val result = controller.listClients(1, 2)

        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(5, result.total)
        assertEquals(2, result.clients.size)
        assertSame(resources[2], result.clients[0])
        assertSame(resources[3], result.clients[1])
    }

    @Test
    fun `listClients - Return empty list when no clients`() = runTest {
        coEvery { clientManager.listClients() } returns emptyList()

        val result = controller.listClients(null, null)

        assertEquals(0, result.total)
        assertTrue(result.clients.isEmpty())
    }

    @Test
    fun `listClients - Return empty page when page exceeds total`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.listClients() } returns listOf(client)

        val result = controller.listClients(5, 20)

        assertEquals(5, result.page)
        assertEquals(1, result.total)
        assertTrue(result.clients.isEmpty())
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
