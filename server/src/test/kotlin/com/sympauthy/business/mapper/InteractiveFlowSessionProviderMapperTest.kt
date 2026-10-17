package com.sympauthy.business.mapper

import com.sympauthy.data.model.InteractiveFlowSessionProviderEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class InteractiveFlowSessionProviderMapperTest {

    private val mapper = InteractiveFlowSessionProviderMapper()

    @Test
    fun `toInteractiveFlowSessionProvider - maps all fields`() {
        val sessionId = UUID.randomUUID()
        val nonceJti = UUID.randomUUID()
        val entity = InteractiveFlowSessionProviderEntity(
            sessionId = sessionId,
            providerId = "provider",
            providerNonceJsonWebTokenId = nonceJti,
        )

        val provider = mapper.toInteractiveFlowSessionProvider(entity)

        assertEquals(sessionId, provider.sessionId)
        assertEquals("provider", provider.providerId)
        assertEquals(nonceJti, provider.providerNonceJsonWebTokenId)
    }

    @Test
    fun `toInteractiveFlowSessionProvider - maps when nonce jti is null`() {
        val sessionId = UUID.randomUUID()
        val entity = InteractiveFlowSessionProviderEntity(
            sessionId = sessionId,
            providerId = "provider",
            providerNonceJsonWebTokenId = null,
        )

        val provider = mapper.toInteractiveFlowSessionProvider(entity)

        assertEquals(sessionId, provider.sessionId)
        assertEquals("provider", provider.providerId)
        assertNull(provider.providerNonceJsonWebTokenId)
    }
}
