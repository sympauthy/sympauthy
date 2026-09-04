package com.sympauthy.business.manager

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.page.PageParams
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AudienceSearchManagerTest {

    @MockK
    lateinit var audienceManager: AudienceManager

    @MockK
    lateinit var clientManager: ClientManager

    @InjectMockKs
    lateinit var audienceSearchManager: AudienceSearchManager

    private val firstPage = PageParams(page = 0, size = 20)

    private fun audience(id: String) = Audience(id = id, tokenAudience = id)

    @Test
    fun `listAudiences - Order by identifier before slicing`() = runTest {
        val admin = audience("admin")
        // Handed last-first, the first page of one still holds the audience the order puts first.
        every { audienceManager.listAudiences() } returns listOf(audience("default"), admin)
        coEvery { clientManager.countClientsByAudienceId() } returns emptyMap()

        val result = audienceSearchManager.listAudiences(PageParams(page = 0, size = 1))

        assertEquals(listOf(admin), result.items.map { it.audience })
        assertEquals(2, result.total)
    }

    @Test
    fun `listAudiences - Answer each audience with the number of clients it groups`() = runTest {
        val default = audience("default")
        every { audienceManager.listAudiences() } returns listOf(default, audience("admin"))
        coEvery { clientManager.countClientsByAudienceId() } returns mapOf("default" to 3)

        val result = audienceSearchManager.listAudiences(firstPage)

        assertEquals(0, result.items.first { it.audience.id == "admin" }.clientCount)
        assertEquals(3, result.items.first { it.audience == default }.clientCount)
    }

    @Test
    fun `findAudienceByIdOrNull - Answer the audience with the number of clients it groups`() = runTest {
        val default = audience("default")
        every { audienceManager.findAudienceByIdOrNull("default") } returns default
        coEvery { clientManager.countClientsByAudienceId() } returns mapOf("default" to 3)

        val result = audienceSearchManager.findAudienceByIdOrNull("default")

        assertEquals(default, result?.audience)
        assertEquals(3, result?.clientCount)
    }

    @Test
    fun `findAudienceByIdOrNull - Answer an audience no client names with 0`() = runTest {
        val admin = audience("admin")
        every { audienceManager.findAudienceByIdOrNull("admin") } returns admin
        coEvery { clientManager.countClientsByAudienceId() } returns mapOf("default" to 3)

        val result = audienceSearchManager.findAudienceByIdOrNull("admin")

        assertEquals(0, result?.clientCount)
    }

    @Test
    fun `findAudienceByIdOrNull - Return null when no audience matches`() = runTest {
        // The clients are left unstubbed: answering without reading them is what this holds.
        every { audienceManager.findAudienceByIdOrNull("unknown") } returns null

        assertNull(audienceSearchManager.findAudienceByIdOrNull("unknown"))
    }
}
