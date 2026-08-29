package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class InteractiveFlowPurposeRegistryTest {

    @Test
    fun `getForPurpose - Returns the handler registered for the purpose`() {
        val handler = mockk<InteractiveFlowPurposeHandler> {
            every { purpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        }
        val registry = InteractiveFlowPurposeRegistry(listOf(handler))

        assertSame(handler, registry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
    }

    @Test
    fun `getForPurpose - Throws when no handler is registered for the purpose`() {
        val registry = InteractiveFlowPurposeRegistry(emptyList())

        val exception = assertThrows<BusinessException> {
            registry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        }
        assertEquals("flow.purpose.unsupported", exception.detailsId)
    }
}
