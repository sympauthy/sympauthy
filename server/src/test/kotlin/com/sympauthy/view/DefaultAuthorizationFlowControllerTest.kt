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
class DefaultAuthorizationFlowControllerTest {

    @MockK
    lateinit var resourceResolver: ResourceResolver

    @InjectMockKs
    lateinit var controller: DefaultAuthorizationFlowController

    @Test
    fun `forward - Answer not_found from a build the flow was not added to`() {
        every { resourceResolver.getResource("classpath:sympauthy-flow/index.html") } returns Optional.empty()

        val exception = assertThrows<LocalizedHttpException> { controller.forward("sign-in") }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals("not_found", exception.detailsId)
    }
}
