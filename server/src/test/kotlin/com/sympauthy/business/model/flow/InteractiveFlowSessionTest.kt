package com.sympauthy.business.model.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class InteractiveFlowSessionTest {

    private fun ongoing(
        purposes: List<InteractiveFlowPurpose>,
        initiatingPurpose: InteractiveFlowPurpose,
    ) = OnGoingInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = purposes,
        initiatingPurpose = initiatingPurpose,
        flowId = null,
        expirationDate = LocalDateTime.now().plusMinutes(5),
        sessionDate = LocalDateTime.now(),
        userId = null,
    )

    @Test
    fun `copy - Preserves the stored initiatingPurpose`() {
        val session = ongoing(
            purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
        )

        val copied = session.copy(userId = UUID.randomUUID())

        assertEquals(InteractiveFlowPurpose.MFA_ENROLLMENT, copied.initiatingPurpose)
    }
}
