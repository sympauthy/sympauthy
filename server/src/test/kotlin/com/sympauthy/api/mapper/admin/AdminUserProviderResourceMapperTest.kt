package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import java.time.LocalDateTime
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers

class AdminUserProviderResourceMapperTest {

    private val mapper: AdminUserProviderResourceMapper =
        Mappers.getMapper(AdminUserProviderResourceMapper::class.java)

    private val linkedAt: LocalDateTime = LocalDateTime.of(2026, 1, 15, 14, 30, 0)

    /**
     * A later sign-in with the same provider, so a resource fed from the fetch or the change date
     * fails the assertion.
     */
    private val lastFetchedAt: LocalDateTime = LocalDateTime.of(2026, 3, 2, 9, 0, 0)

    @Test
    fun `toResource - Publish the provider, the subject it knows the user by and the date it was linked`() {
        val resource = mapper.toResource(
            ProviderUserInfo(
                providerId = "discord",
                userId = UUID.randomUUID(),
                linkDate = linkedAt,
                fetchDate = lastFetchedAt,
                changeDate = lastFetchedAt,
                sessionId = null,
                userInfo = RawProviderClaims(subject = "123456789012345678")
            )
        )

        assertEquals("discord", resource.providerId)
        assertEquals("123456789012345678", resource.subject)
        assertEquals(linkedAt, resource.linkedAt)
    }
}
