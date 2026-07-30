package com.sympauthy.business.mapper

import com.sympauthy.data.model.InteractiveFlowSessionMfaEnrollmentEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.util.*

class InteractiveFlowSessionMfaEnrollmentMapperTest {

    private val mapper = Mappers.getMapper(InteractiveFlowSessionMfaEnrollmentMapper::class.java)

    @Test
    fun `toInteractiveFlowSessionMfaEnrollment - maps all fields`() {
        val sessionId = UUID.randomUUID()
        val entity = InteractiveFlowSessionMfaEnrollmentEntity(
            sessionId = sessionId,
            returnUri = "https://client.example.com/enrolled",
        )

        val enrollment = mapper.toInteractiveFlowSessionMfaEnrollment(entity)

        assertEquals(sessionId, enrollment.sessionId)
        assertEquals("https://client.example.com/enrolled", enrollment.returnUri)
    }
}
