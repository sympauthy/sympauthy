package com.sympauthy.business.manager

import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.page.PageParams
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

@ExtendWith(MockKExtension::class)
class ClientSearchManagerTest {

    @MockK
    lateinit var clientManager: ClientManager

    @InjectMockKs
    lateinit var clientSearchManager: ClientSearchManager

    private fun client(clientId: String): Client = mockk {
        every { id } returns clientId
    }

    @Test
    fun `listClients - Order by identifier before slicing`() = runTest {
        val alpha = client("alpha")
        // Handed last-first, the first page of one still holds the client the order puts first.
        coEvery { clientManager.listClients() } returns listOf(client("zulu"), alpha)

        val result = clientSearchManager.listClients(PageParams(page = 0, size = 1))

        assertEquals(listOf(alpha), result.items)
        assertEquals(2, result.total)
    }

    @Test
    fun `listClients - Return the page the parameters name`() = runTest {
        coEvery { clientManager.listClients() } returns listOf(client("a"), client("b"), client("c"))

        val result = clientSearchManager.listClients(PageParams(page = 1, size = 2))

        assertEquals(listOf("c"), result.items.map { it.id })
        assertEquals(3, result.total)
    }
}
