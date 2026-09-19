package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminInteractiveFlowSessionResourceMapper
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDetailResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSecurityContextResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSummaryResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionSearchManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionSearchManager.InteractiveFlowSessionDetail
import com.sympauthy.business.manager.flow.InteractiveFlowSessionSearchManager.InteractiveFlowSessionSummary
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionSecurityContext
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.SortOrder
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminInteractiveFlowSessionControllerTest {

    @MockK
    lateinit var searchManager: InteractiveFlowSessionSearchManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var sessionMapper: AdminInteractiveFlowSessionResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminInteractiveFlowSessionController

    private val sessionId: UUID = UUID.randomUUID()

    @Test
    fun `listInteractiveFlowSessions - Map every session the page holds, and publish the page it came in`() =
        runTest {
            val summary = mockk<InteractiveFlowSessionSummary>()
            val resource = mockk<AdminInteractiveFlowSessionSummaryResource>()
            givenClients("web-app")
            coEvery {
                searchManager.listSessions(null, null, null, null, null, null, PageParams(0, 20))
            } returns Page(items = listOf(summary), page = 3, size = 7, total = 42)
            every { sessionMapper.toResource(summary) } returns resource

            val result = controller.listInteractiveFlowSessions(
                null, null, null, null, null, null, null, null
            )

            assertSame(resource, result.sessions.single())
            assertEquals(3, result.page)
            assertEquals(7, result.size)
            assertEquals(42, result.total)
        }

    @Test
    fun `listInteractiveFlowSessions - Resolve every criterion into the domain value it names`() = runTest {
        givenClients("web-app")
        val userId = UUID.randomUUID()
        coEvery {
            searchManager.listSessions(
                "203.0.113.",
                "web-app",
                userId,
                InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
                InteractiveFlowSessionStatus.EXPIRED,
                SortOrder.DESC,
                PageParams(0, 20)
            )
        } returns Page(items = emptyList(), page = 0, size = 20, total = 0)

        val result = controller.listInteractiveFlowSessions(
            null, null, "203.0.113.", "web-app", userId, "oauth2_authorize", "expired", "desc"
        )

        assertEquals(0, result.total)
    }

    @Test
    fun `listInteractiveFlowSessions - Refuse a client this deployment does not have`() = runTest {
        // The search is not stubbed on purpose: reaching the assertion is proof that a client naming
        // nothing is refused before anything is read.
        givenClients("web-app")

        val exception = assertThrows<LocalizedHttpException> {
            controller.listInteractiveFlowSessions(null, null, null, "nope", null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listInteractiveFlowSessions - Refuse a purpose the set does not hold`() = runTest {
        givenClients("web-app")

        val exception = assertThrows<LocalizedHttpException> {
            controller.listInteractiveFlowSessions(null, null, null, null, null, "sign_in", null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listInteractiveFlowSessions - Refuse a status the set does not hold`() = runTest {
        givenClients("web-app")

        val exception = assertThrows<LocalizedHttpException> {
            controller.listInteractiveFlowSessions(null, null, null, null, null, null, "stuck", null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listInteractiveFlowSessions - Refuse an order naming neither direction`() = runTest {
        givenClients("web-app")

        val exception = assertThrows<LocalizedHttpException> {
            controller.listInteractiveFlowSessions(null, null, null, null, null, null, null, "ascending")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("order.value.unsupported", exception.detailsId)
    }

    @Test
    fun `getInteractiveFlowSession - Publish the session the identifier names`() = runTest {
        val detail = mockk<InteractiveFlowSessionDetail>()
        val resource = mockk<AdminInteractiveFlowSessionDetailResource>()
        coEvery { searchManager.findSessionOrNull(sessionId) } returns detail
        every { sessionMapper.toResource(detail) } returns resource

        assertSame(resource, controller.getInteractiveFlowSession(sessionId))
    }

    @Test
    fun `getInteractiveFlowSession - Answer not found for a session already collected`() = runTest {
        coEvery { searchManager.findSessionOrNull(sessionId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.getInteractiveFlowSession(sessionId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `listInteractiveFlowSessionSecurityContexts - Map every place the page holds, and publish the page`() =
        runTest {
            val place = mockk<InteractiveFlowSessionSecurityContext>()
            val resource = mockk<AdminInteractiveFlowSessionSecurityContextResource>()
            coEvery {
                searchManager.listSecurityContexts(sessionId, PageParams(0, 20))
            } returns Page(items = listOf(place), page = 2, size = 5, total = 9)
            every { sessionMapper.toResource(place) } returns resource

            val result = controller.listInteractiveFlowSessionSecurityContexts(sessionId, null, null)

            assertSame(resource, result.securityContexts.single())
            assertEquals(2, result.page)
            assertEquals(5, result.size)
            assertEquals(9, result.total)
        }

    @Test
    fun `listInteractiveFlowSessionSecurityContexts - Answer not found for a session already collected`() =
        runTest {
            coEvery { searchManager.listSecurityContexts(sessionId, PageParams(0, 20)) } returns null

            val exception = assertThrows<LocalizedHttpException> {
                controller.listInteractiveFlowSessionSecurityContexts(sessionId, null, null)
            }

            assertEquals(HttpStatus.NOT_FOUND, exception.status)
        }

    private fun givenClients(vararg ids: String) {
        coEvery { clientManager.listClients() } returns ids.map { id ->
            mockk<Client> { every { this@mockk.id } returns id }
        }
    }
}
