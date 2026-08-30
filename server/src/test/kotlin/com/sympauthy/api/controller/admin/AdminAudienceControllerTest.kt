package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminAudienceResourceMapper
import com.sympauthy.api.resource.admin.AdminAudienceResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.DEFAULT_PAGE_SIZE
import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.audience.Audience
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
class AdminAudienceControllerTest {

    @MockK
    lateinit var audienceManager: AudienceManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var audienceMapper: AdminAudienceResourceMapper

    @InjectMockKs
    lateinit var controller: AdminAudienceController

    private fun audience(id: String) = Audience(id = id, tokenAudience = id)

    @Test
    fun `listAudiences - Return paginated list with defaults`() = runTest {
        val first = audience("a")
        val second = audience("b")
        val firstResource = mockk<AdminAudienceResource>()
        val secondResource = mockk<AdminAudienceResource>()

        every { audienceManager.listAudiences() } returns listOf(first, second)
        coEvery { clientManager.countClientsByAudienceId() } returns mapOf("a" to 2)
        every { audienceMapper.toResource(first, 2) } returns firstResource
        every { audienceMapper.toResource(second, 0) } returns secondResource

        val result = controller.listAudiences(null, null)

        assertEquals(DEFAULT_PAGE, result.page)
        assertEquals(DEFAULT_PAGE_SIZE, result.size)
        assertEquals(2, result.total)
        assertSame(firstResource, result.audiences[0])
        assertSame(secondResource, result.audiences[1])
    }

    @Test
    fun `listAudiences - Order by identifier`() = runTest {
        val zulu = audience("zulu")
        val alpha = audience("alpha")
        val zuluResource = mockk<AdminAudienceResource>()
        val alphaResource = mockk<AdminAudienceResource>()

        every { audienceManager.listAudiences() } returns listOf(zulu, alpha)
        coEvery { clientManager.countClientsByAudienceId() } returns emptyMap()
        every { audienceMapper.toResource(zulu, 0) } returns zuluResource
        every { audienceMapper.toResource(alpha, 0) } returns alphaResource

        val result = controller.listAudiences(null, null)

        assertSame(alphaResource, result.audiences[0])
        assertSame(zuluResource, result.audiences[1])
    }

    @Test
    fun `listAudiences - Apply page and size`() = runTest {
        val audiences = listOf("e", "d", "c", "b", "a").map(::audience)
        val thirdResource = mockk<AdminAudienceResource>()
        val fourthResource = mockk<AdminAudienceResource>()

        every { audienceManager.listAudiences() } returns audiences
        coEvery { clientManager.countClientsByAudienceId() } returns emptyMap()
        // Ordered by identifier, the second page of two holds c and d.
        every { audienceMapper.toResource(audience("c"), 0) } returns thirdResource
        every { audienceMapper.toResource(audience("d"), 0) } returns fourthResource

        val result = controller.listAudiences(1, 2)

        assertEquals(1, result.page)
        assertEquals(5, result.total)
        assertSame(thirdResource, result.audiences[0])
        assertSame(fourthResource, result.audiences[1])
    }

    @Test
    fun `getAudience - Throw 404 when audience not found`() = runTest {
        every { audienceManager.findAudienceByIdOrNull("unknown") } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.getAudience("unknown")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}
