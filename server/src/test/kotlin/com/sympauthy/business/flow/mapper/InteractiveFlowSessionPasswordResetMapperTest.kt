package com.sympauthy.business.flow.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.data.model.InteractiveFlowSessionPasswordResetEntity
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class InteractiveFlowSessionPasswordResetMapperTest {

    private val mapper = InteractiveFlowSessionPasswordResetMapper()
    private val requestedDate = LocalDateTime.of(2026, 11, 15, 10, 0)

    private fun entity(
        salt: ByteArray = ByteArray(0),
        hashedPassword: ByteArray = ByteArray(0),
        passwordChosenDate: LocalDateTime? = null
    ) = InteractiveFlowSessionPasswordResetEntity(
        sessionId = UUID.randomUUID(),
        login = "person@example.com",
        userId = UUID.randomUUID(),
        requestedDate = requestedDate,
        codeProvenDate = requestedDate.plusMinutes(1),
        salt = salt,
        hashedPassword = hashedPassword,
        passwordChosenDate = passwordChosenDate
    )

    @Test
    fun `toInteractiveFlowSessionPasswordReset - Reads no new password before one is chosen`() {
        val reset = mapper.toInteractiveFlowSessionPasswordReset(entity())

        assertNull(reset.newPassword)
        assertNull(reset.passwordChosenDate)
    }

    @Test
    fun `toInteractiveFlowSessionPasswordReset - Reads the new password once one is chosen`() {
        val chosenDate = requestedDate.plusMinutes(2)
        val reset = mapper.toInteractiveFlowSessionPasswordReset(
            entity(salt = byteArrayOf(1), hashedPassword = byteArrayOf(2), passwordChosenDate = chosenDate)
        )

        assertArrayEquals(byteArrayOf(1), reset.newPassword?.salt)
        assertArrayEquals(byteArrayOf(2), reset.newPassword?.hash)
        assertEquals(chosenDate, reset.passwordChosenDate)
    }

    @Test
    fun `toInteractiveFlowSessionPasswordReset - Refuses a chosen password with no hash`() {
        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionPasswordReset(
                entity(salt = byteArrayOf(1), passwordChosenDate = requestedDate.plusMinutes(2))
            )
        }

        assertEquals("mapper.interactive_flow_session_password_reset.invalid_property", exception.detailsId)
        assertEquals("hashedPassword", exception.values["property"])
    }

    @Test
    fun `toInteractiveFlowSessionPasswordReset - Refuses a hash with no date it was chosen at`() {
        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionPasswordReset(
                entity(salt = byteArrayOf(1), hashedPassword = byteArrayOf(2))
            )
        }

        assertEquals("mapper.interactive_flow_session_password_reset.invalid_property", exception.detailsId)
        assertEquals("hashedPassword", exception.values["property"])
    }
}
