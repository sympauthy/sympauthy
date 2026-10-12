package com.sympauthy.view

import com.sympauthy.api.exception.LocalizedHttpException
import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.HttpStatus
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminUiControllerTest {

    @MockK
    lateinit var resourceResolver: ResourceResolver

    @InjectMockKs
    lateinit var controller: AdminUiController

    @Test
    fun `root - Answer not_found from a build the admin console was not added to`() {
        every { resourceResolver.getResource("classpath:sympauthy-admin/index.html") } returns Optional.empty()

        val exception = assertThrows<LocalizedHttpException> { controller.root() }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals("not_found", exception.detailsId)
    }

    @Test
    fun `forward - Answer not_found from a build the admin console was not added to`() {
        every { resourceResolver.getResource("classpath:sympauthy-admin/index.html") } returns Optional.empty()

        val exception = assertThrows<LocalizedHttpException> { controller.forward("users") }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals("not_found", exception.detailsId)
    }
}
