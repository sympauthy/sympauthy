package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.data.model.InteractiveFlowSessionConfirmEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class InteractiveFlowSessionConfirmMapperTest {

    private val mapper = Mappers.getMapper(InteractiveFlowSessionConfirmMapper::class.java)

    @Test
    fun `toInteractiveFlowSessionConfirm - maps all fields`() {
        val sessionId = UUID.randomUUID()
        val confirmedDate = LocalDateTime.now().minusMinutes(1)
        val entity = InteractiveFlowSessionConfirmEntity(
            sessionId = sessionId,
            action = ConfirmActionType.LINK_PROVIDER.name,
            clientId = "client",
            confirmedDate = confirmedDate,
        )

        val confirm = mapper.toInteractiveFlowSessionConfirm(entity)

        assertEquals(sessionId, confirm.sessionId)
        assertEquals(ConfirmActionType.LINK_PROVIDER, confirm.action)
        assertEquals("client", confirm.clientId)
        assertEquals(confirmedDate, confirm.confirmedDate)
    }

    @Test
    fun `toInteractiveFlowSessionConfirm - maps an admin-initiated confirmation with no client`() {
        val entity = InteractiveFlowSessionConfirmEntity(
            sessionId = UUID.randomUUID(),
            action = ConfirmActionType.ENROLL_MFA.name,
        )

        val confirm = mapper.toInteractiveFlowSessionConfirm(entity)

        assertNull(confirm.clientId)
        assertNull(confirm.confirmedDate)
    }

    @Test
    fun `toInteractiveFlowSessionConfirm - throws when action is unknown`() {
        val entity = InteractiveFlowSessionConfirmEntity(
            sessionId = UUID.randomUUID(),
            action = "UNKNOWN",
        )

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionConfirm(entity)
        }
        assertEquals("mapper.interactive_flow_session_confirm.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }
}
