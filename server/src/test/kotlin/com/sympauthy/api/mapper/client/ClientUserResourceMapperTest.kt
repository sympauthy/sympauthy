package com.sympauthy.api.mapper.client

import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class ClientUserResourceMapperTest {

    private val mapper: ClientUserResourceMapper =
        Mappers.getMapper(ClientUserResourceMapper::class.java)

    private val linkedAt: LocalDateTime = LocalDateTime.of(2026, 1, 15, 14, 30, 0)

    /**
     * A later sign-in with the same provider, so a resource fed from either of the two moving dates
     * fails the assertion rather than passing on a coincidence.
     */
    private val lastFetchedAt: LocalDateTime = LocalDateTime.of(2026, 3, 2, 9, 0, 0)

    @Test
    fun `toProviderResource - Reports the link date, not the date the claims were last fetched`() {
        val resource = mapper.toProviderResource(
            ProviderUserInfo(
                providerId = "discord",
                userId = UUID.randomUUID(),
                linkDate = linkedAt,
                fetchDate = lastFetchedAt,
                changeDate = lastFetchedAt,
                userInfo = RawProviderClaims(subject = "123456789012345678")
            )
        )

        assertEquals("discord", resource.providerId)
        assertEquals("123456789012345678", resource.subject)
        assertEquals(linkedAt, resource.linkedAt)
    }
}
