package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.client.Client
import com.sympauthy.config.model.ClientsConfig
import com.sympauthy.config.model.EnabledClientsConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClientManagerTest {

    @Test
    fun `listClients - Return list of clients from config`() = runTest {
        val client1 = mockk<Client> {
            every { id } returns "client1"
        }
        val client2 = mockk<Client> {
            every { id } returns "client2"
        }
        val clientsConfig = EnabledClientsConfig(clients = listOf(client1, client2))
        val uncheckedClientsConfig = flowOf<ClientsConfig>(clientsConfig)

        val clientManager = ClientManager(uncheckedClientsConfig)

        val result = clientManager.listClients()

        assertEquals(2, result.size)
        assertSame(client1, result[0])
        assertSame(client2, result[1])
    }

    @Test
    fun `listClients - Return empty list when config is null`() = runTest {
        val uncheckedClientsConfig = flowOf<ClientsConfig>()

        val clientManager = ClientManager(uncheckedClientsConfig)

        val result = clientManager.listClients()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findClientByIdOrNull - Return client when found`() = runTest {
        val client1 = mockk<Client> {
            every { id } returns "client1"
        }
        val client2 = mockk<Client> {
            every { id } returns "client2"
        }
        val clientsConfig = EnabledClientsConfig(clients = listOf(client1, client2))
        val uncheckedClientsConfig = flowOf<ClientsConfig>(clientsConfig)

        val clientManager = ClientManager(uncheckedClientsConfig)

        val result = clientManager.findClientByIdOrNull("client2")

        assertSame(client2, result)
    }

    @Test
    fun `findClientByIdOrNull - Return null when client not found`() = runTest {
        val client1 = mockk<Client> {
            every { id } returns "client1"
        }
        val clientsConfig = EnabledClientsConfig(clients = listOf(client1))
        val uncheckedClientsConfig = flowOf<ClientsConfig>(clientsConfig)

        val clientManager = ClientManager(uncheckedClientsConfig)

        val result = clientManager.findClientByIdOrNull("nonexistent")

        assertNull(result)
    }

    @Test
    fun `findClientById - Return client when found`() = runTest {
        val client1 = mockk<Client> {
            every { id } returns "client1"
        }
        val client2 = mockk<Client> {
            every { id } returns "client2"
        }
        val clientsConfig = EnabledClientsConfig(clients = listOf(client1, client2))
        val uncheckedClientsConfig = flowOf<ClientsConfig>(clientsConfig)

        val clientManager = ClientManager(uncheckedClientsConfig)

        val result = clientManager.findClientById("client1")

        assertSame(client1, result)
    }

    @Test
    fun `findClientById - Throw BusinessException when client not found`() = runTest {
        val client1 = mockk<Client> {
            every { id } returns "client1"
        }
        val clientsConfig = EnabledClientsConfig(clients = listOf(client1))
        val uncheckedClientsConfig = flowOf<ClientsConfig>(clientsConfig)

        val clientManager = ClientManager(uncheckedClientsConfig)

        val exception = assertThrows<BusinessException> {
            clientManager.findClientById("nonexistent")
        }

        assertEquals("client.invalid_client_id", exception.detailsId)
    }

    @Test
    fun `authenticateClientOrNull - Return client when clientId and clientSecret match`() = runTest {
        val client1 = mockk<Client> {
            every { id } returns "client1"
            every { secret } returns "secret1"
        }
        val client2 = mockk<Client> {
            every { id } returns "client2"
            every { secret } returns "secret2"
        }
        val clientsConfig = EnabledClientsConfig(clients = listOf(client1, client2))
        val uncheckedClientsConfig = flowOf<ClientsConfig>(clientsConfig)

        val clientManager = ClientManager(uncheckedClientsConfig)

        val result = clientManager.authenticateClientOrNull("client2", "secret2")

        assertSame(client2, result)
    }

    @Test
    fun `authenticateClientOrNull - Return null when clientId does not exist`() = runTest {
        val client1 = mockk<Client> {
            every { id } returns "client1"
            every { secret } returns "secret1"
        }
        val clientsConfig = EnabledClientsConfig(clients = listOf(client1))
        val uncheckedClientsConfig = flowOf<ClientsConfig>(clientsConfig)

        val clientManager = ClientManager(uncheckedClientsConfig)

        val result = clientManager.authenticateClientOrNull("nonexistent", "secret1")

        assertNull(result)
    }

    @Test
    fun `authenticateClientOrNull - Return null when clientSecret does not match`() = runTest {
        val client1 = mockk<Client> {
            every { id } returns "client1"
            every { secret } returns "secret1"
        }
        val clientsConfig = EnabledClientsConfig(clients = listOf(client1))
        val uncheckedClientsConfig = flowOf<ClientsConfig>(clientsConfig)

        val clientManager = ClientManager(uncheckedClientsConfig)

        val result = clientManager.authenticateClientOrNull("client1", "wrongsecret")

        assertNull(result)
    }
}
