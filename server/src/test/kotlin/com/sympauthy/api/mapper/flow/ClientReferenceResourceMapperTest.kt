package com.sympauthy.api.mapper.flow

import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.client.ClientReference
import com.sympauthy.business.model.flow.InteractiveFlowSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ClientReferenceResourceMapperTest {

    @MockK
    lateinit var clientManager: ClientManager

    @InjectMockKs
    lateinit var mapper: ClientReferenceResourceMapper

    @Test
    fun `toResourceOrNull - Name the client the session was started for, with its display name`() = runTest {
        val session = mockk<InteractiveFlowSession> { every { initiatingClientId } returns "billing-web" }
        coEvery { clientManager.findClientReference("billing-web") } returns
            ClientReference(id = "billing-web", displayName = "Billing portal")

        val resource = mapper.toResourceOrNull(session)

        assertEquals("billing-web", resource?.id)
        assertEquals("Billing portal", resource?.displayName)
    }

    @Test
    fun `toResourceOrNull - Name no client on a session no client started`() = runTest {
        val session = mockk<InteractiveFlowSession> { every { initiatingClientId } returns null }

        assertNull(mapper.toResourceOrNull(session))
    }
}
