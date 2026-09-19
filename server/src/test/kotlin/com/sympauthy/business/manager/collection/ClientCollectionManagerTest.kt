package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.collection.criteriaOf
import com.sympauthy.business.model.page.PageParams
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
class ClientCollectionManagerTest {

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var audienceManager: AudienceManager

    @InjectMockKs
    lateinit var clientCollectionManager: ClientCollectionManager

    private val firstPage = PageParams(page = 0, size = 20)

    private fun audience(id: String) = Audience(id = id, tokenAudience = id)

    private fun client(clientId: String, audienceId: String = "default") = Client(
        id = clientId,
        secret = null,
        audience = audience(audienceId),
        allowedGrantTypes = setOf(GrantType.AUTHORIZATION_CODE),
        authorizationFlow = null
    )

    private fun knownClients(vararg clients: Client) {
        coEvery { clientManager.listClients() } returns clients.toList()
        every { audienceManager.listAudiences() } returns listOf(audience("default"), audience("admin"))
    }

    private suspend fun criteriaOf(vararg filters: Pair<String, String>) =
        clientCollectionManager.capabilities().criteriaOf(*filters)

    @Test
    fun `listClients - Order by identifier before slicing`() = runTest {
        val alpha = client("alpha")
        // Handed last-first, the first page of one still holds the client the order puts first.
        knownClients(client("zulu"), alpha)

        val result = clientCollectionManager.listClients(criteriaOf(), PageParams(page = 0, size = 1))

        assertEquals(listOf(alpha), result.items)
        assertEquals(2, result.total)
    }

    @Test
    fun `listClients - Keep the clients the audience a criterion names groups`() = runTest {
        val admin = client("b", audienceId = "admin")
        knownClients(client("a"), admin)

        val result = clientCollectionManager.listClients(criteriaOf("audience_id" to "admin"), firstPage)

        assertEquals(listOf(admin), result.items)
    }

    @Test
    fun `listClients - Keep the clients an identifier is a fragment of`() = runTest {
        val mobile = client("mobile-app")
        knownClients(client("web-app"), mobile)

        val result = clientCollectionManager.listClients(criteriaOf("id.starts_with" to "MOBILE"), firstPage)

        assertEquals(listOf(mobile), result.items)
    }

    @Test
    fun `listClients - Return the page the parameters name`() = runTest {
        knownClients(client("a"), client("b"), client("c"))

        val result = clientCollectionManager.listClients(criteriaOf(), PageParams(page = 1, size = 2))

        assertEquals(listOf("c"), result.items.map { it.id })
        assertEquals(3, result.total)
    }
}
