package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminInteractiveFlowSessionResourceMapper
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDetailResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSecurityContextResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSummaryResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.api.util.noCapabilities
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager.InteractiveFlowSessionDetail
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager.InteractiveFlowSessionSummary
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldType
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.enumFieldValues
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionSecurityContext
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.util.DEFAULT_LOCALE
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
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
    lateinit var sessionCollectionManager: InteractiveFlowSessionCollectionManager

    @MockK
    lateinit var sessionMapper: AdminInteractiveFlowSessionResourceMapper

    @MockK
    lateinit var capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminInteractiveFlowSessionController

    private val sessionId: UUID = UUID.randomUUID()

    private val clientField = CollectionField(
        name = "client",
        type = CollectionFieldType.ENUM,
        key = "fields.client_id",
        operators = setOf(CollectionOperator.EQ),
        values = listOf(CollectionFieldValue("web-app", "fields.client_id.web-app")),
        searchable = true
    )

    private val purposeField = CollectionField(
        name = "purpose",
        type = CollectionFieldType.ENUM,
        key = "fields.interactive_flow_purpose",
        operators = setOf(CollectionOperator.EQ),
        values = enumFieldValues<InteractiveFlowPurpose>("fields.interactive_flow_purpose"),
        sortable = true
    )

    private val capabilities = CollectionCapabilities(listOf(clientField, purposeField), emptyList())

    @Test
    fun `listInteractiveFlowSessions - Map every session the page holds, and publish the page it came in`() =
        runTest {
            val summary = mockk<InteractiveFlowSessionSummary>()
            val resource = mockk<AdminInteractiveFlowSessionSummaryResource>()
            coEvery { sessionCollectionManager.capabilities() } returns capabilities
            coEvery {
                sessionCollectionManager.listSessions(any(), PageParams(0, 20))
            } returns Page(items = listOf(summary), page = 3, size = 7, total = 42)
            every { sessionMapper.toResource(summary) } returns resource

            val result = controller.listInteractiveFlowSessions(collectionRequest(), null, null, null, null)

            assertSame(resource, result.sessions.single())
            assertEquals(3, result.page)
            assertEquals(7, result.size)
            assertEquals(42, result.total)
        }

    @Test
    fun `listInteractiveFlowSessions - Hand the manager every criterion the request carries`() = runTest {
        val criteria = slot<CollectionCriteria>()
        coEvery { sessionCollectionManager.capabilities() } returns capabilities
        coEvery {
            sessionCollectionManager.listSessions(capture(criteria), PageParams(0, 20))
        } returns Page(items = emptyList(), page = 0, size = 20, total = 0)

        val result = controller.listInteractiveFlowSessions(
            collectionRequest("client=web-app&purpose=oauth2_authorize"),
            null, null, "-purpose", "203.0.113."
        )

        assertEquals(0, result.total)
        assertEquals(
            listOf("client" to listOf("web-app"), "purpose" to listOf("oauth2_authorize")),
            criteria.captured.filters.map { it.field.name to it.values }
        )
        assertEquals("-purpose", criteria.captured.sort.single().spelling)
        assertEquals("203.0.113.", criteria.captured.query)
    }

    @Test
    fun `listInteractiveFlowSessions - Refuse a client this deployment does not have`() = runTest {
        // The manager is not stubbed on purpose: reaching the assertion is proof that a client naming
        // nothing is refused before anything is read.
        coEvery { sessionCollectionManager.capabilities() } returns capabilities

        val exception = assertThrows<LocalizedHttpException> {
            controller.listInteractiveFlowSessions(collectionRequest("client=nope"), null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listInteractiveFlowSessions - Refuse a purpose the set does not hold`() = runTest {
        coEvery { sessionCollectionManager.capabilities() } returns capabilities

        val exception = assertThrows<LocalizedHttpException> {
            controller.listInteractiveFlowSessions(collectionRequest("purpose=sign_in"), null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listInteractiveFlowSessions - Refuse a sort key this collection is not ordered by`() = runTest {
        coEvery { sessionCollectionManager.capabilities() } returns capabilities

        val exception = assertThrows<LocalizedHttpException> {
            controller.listInteractiveFlowSessions(collectionRequest(), null, null, "client", null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.sort.unknown_field", exception.detailsId)
    }

    @Test
    fun `getInteractiveFlowSession - Publish the session the identifier names, read in the language asked for`() =
        runTest {
            val detail = mockk<InteractiveFlowSessionDetail>()
            val resource = mockk<AdminInteractiveFlowSessionDetailResource>()
            coEvery { sessionCollectionManager.findSessionOrNull(sessionId) } returns detail
            // Only the locale the request asks for is answered, so reaching the assertion is what proves
            // the messages are read in it rather than in the server's own.
            every { sessionMapper.toResource(detail, Locale.FRANCE) } returns resource

            assertSame(resource, controller.getInteractiveFlowSession(localeRequest(Locale.FRANCE), sessionId))
        }

    @Test
    fun `getInteractiveFlowSession - Read a session for a request naming no language in the default one`() =
        runTest {
            val detail = mockk<InteractiveFlowSessionDetail>()
            val resource = mockk<AdminInteractiveFlowSessionDetailResource>()
            coEvery { sessionCollectionManager.findSessionOrNull(sessionId) } returns detail
            every { sessionMapper.toResource(detail, DEFAULT_LOCALE) } returns resource

            assertSame(resource, controller.getInteractiveFlowSession(collectionRequest(), sessionId))
        }

    @Test
    fun `getInteractiveFlowSession - Answer not found for a session already collected`() = runTest {
        coEvery { sessionCollectionManager.findSessionOrNull(sessionId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.getInteractiveFlowSession(collectionRequest(), sessionId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    private fun localeRequest(locale: Locale) = mockk<HttpRequest<*>> {
        every { this@mockk.locale } returns Optional.of(locale)
    }

    @Test
    fun `listInteractiveFlowSessionSecurityContexts - Map every place the page holds, and publish the page`() =
        runTest {
            val place = mockk<InteractiveFlowSessionSecurityContext>()
            val resource = mockk<AdminInteractiveFlowSessionSecurityContextResource>()
            coEvery { sessionCollectionManager.securityContextCapabilities() } returns noCapabilities()
            coEvery {
                sessionCollectionManager.listSecurityContexts(sessionId, any(), PageParams(0, 20))
            } returns Page(items = listOf(place), page = 2, size = 5, total = 9)
            every { sessionMapper.toResource(place) } returns resource

            val result = controller.listInteractiveFlowSessionSecurityContexts(
                collectionRequest(), sessionId, null, null, null, null
            )

            assertSame(resource, result.securityContexts.single())
            assertEquals(2, result.page)
            assertEquals(5, result.size)
            assertEquals(9, result.total)
        }

    @Test
    fun `listInteractiveFlowSessionSecurityContexts - Answer not found for a session already collected`() =
        runTest {
            coEvery { sessionCollectionManager.securityContextCapabilities() } returns noCapabilities()
            coEvery { sessionCollectionManager.listSecurityContexts(sessionId, any(), PageParams(0, 20)) } returns null

            val exception = assertThrows<LocalizedHttpException> {
                controller.listInteractiveFlowSessionSecurityContexts(
                    collectionRequest(), sessionId, null, null, null, null
                )
            }

            assertEquals(HttpStatus.NOT_FOUND, exception.status)
        }
}
