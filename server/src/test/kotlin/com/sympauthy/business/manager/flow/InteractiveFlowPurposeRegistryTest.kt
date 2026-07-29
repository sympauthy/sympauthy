package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InteractiveFlowPurposeRegistryTest {

    @Test
    fun `getForSession - Returns the handler registered for the session purpose`() {
        val handler = mockk<InteractiveFlowPurposeHandler> {
            every { purpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        }
        val registry = InteractiveFlowPurposeRegistry(listOf(handler))
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { purpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        }

        assertSame(handler, registry.getForSession(session))
    }

    @Test
    fun `getForSession - Throws when no handler is registered for the session purpose`() {
        val registry = InteractiveFlowPurposeRegistry(emptyList())
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { purpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        }

        val exception = assertThrows<BusinessException> {
            registry.getForSession(session)
        }
        assertEquals("flow.purpose.unsupported", exception.detailsId)
    }
}
