package com.sympauthy.api.mapper

import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.mapper.LocalizedErrorMapper
import com.sympauthy.exception.model.LocalizedError
import com.sympauthy.exception.model.LocalizedPropertyError
import io.micronaut.http.HttpStatus.BAD_REQUEST
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class ErrorResourceMapperTest {

    @MockK
    lateinit var localizedErrorMapper: LocalizedErrorMapper

    @InjectMockKs
    lateinit var mapper: ErrorResourceMapper

    private val exception = mockk<LocalizedException>()

    private fun localizedError(vararg properties: LocalizedPropertyError) = LocalizedError(
        httpStatus = BAD_REQUEST,
        errorCode = "flow.claims.invalid",
        description = "One or more of the values you submitted were refused.",
        details = null,
        properties = properties.toList()
    )

    @Test
    fun `toResource - Publish an entry per refused property`() {
        every { localizedErrorMapper.toLocalizedError(exception, Locale.US) } returns localizedError(
            LocalizedPropertyError(
                path = "email",
                errorCode = "user.claim_value_validator.invalid_type",
                description = "The value provided is not of the expected type (email).",
                details = "Invalid type, expected value to be of type email."
            ),
            LocalizedPropertyError(
                path = "birthdate",
                errorCode = "user.claim_value_validator.invalid_date",
                description = "Please provide a date formatted as YYYY-MM-DD.",
                details = null
            )
        )

        val resource = mapper.toResource(exception, Locale.US)

        val properties = requireNotNull(resource.properties)
        assertEquals(listOf("email", "birthdate"), properties.map { it.path })
        assertEquals("user.claim_value_validator.invalid_type", properties.first().errorCode)
        assertEquals("The value provided is not of the expected type (email).", properties.first().description)
        assertEquals("Invalid type, expected value to be of type email.", properties.first().details)
        assertNull(properties.last().details)
    }

    @Test
    fun `toResource - Carry no properties at all for an error refusing none`() {
        every { localizedErrorMapper.toLocalizedError(exception, Locale.US) } returns localizedError()

        // Absent rather than an empty list: a client tests for the presence of the key.
        assertNull(mapper.toResource(exception, Locale.US).properties)
    }
}
