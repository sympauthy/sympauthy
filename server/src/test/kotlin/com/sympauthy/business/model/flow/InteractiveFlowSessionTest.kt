package com.sympauthy.business.model.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class InteractiveFlowSessionTest {

    private fun ongoing(
        purposes: List<InteractiveFlowPurpose>,
        initiatingPurpose: InteractiveFlowPurpose = purposes.first(),
        version: Long = 0,
    ) = OnGoingInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = purposes,
        initiatingPurpose = initiatingPurpose,
        flowId = null,
        expirationDate = LocalDateTime.now().plusMinutes(5),
        sessionDate = LocalDateTime.now(),
        version = version,
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

    @Test
    fun `copy - Preserves the current version when not overridden`() {
        val session = ongoing(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE), version = 4)

        val copy = session.copy(userId = UUID.randomUUID())

        assertEquals(4L, copy.version)
    }

    @Test
    fun `copy - Overrides the version when provided`() {
        val session = ongoing(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE), version = 4)

        val copy = session.copy(version = 5)

        assertEquals(5L, copy.version)
    }
}
