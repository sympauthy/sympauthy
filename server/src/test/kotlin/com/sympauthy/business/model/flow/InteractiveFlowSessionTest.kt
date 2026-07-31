package com.sympauthy.business.model.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class InteractiveFlowSessionTest {

    private fun ongoing(purposes: List<InteractiveFlowPurpose>) = OnGoingInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = purposes,
        flowId = null,
        expirationDate = LocalDateTime.now().plusMinutes(5),
        sessionDate = LocalDateTime.now(),
        userId = null,
    )

    @Test
    fun `initiatingPurpose - Skips the CONFIRM gate to the first real purpose`() {
        val session = ongoing(listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT))

        assertEquals(InteractiveFlowPurpose.MFA_ENROLLMENT, session.initiatingPurpose)
    }

    @Test
    fun `initiatingPurpose - Is the first purpose when there is no CONFIRM gate`() {
        val session = ongoing(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE))

        assertEquals(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, session.initiatingPurpose)
    }
}
