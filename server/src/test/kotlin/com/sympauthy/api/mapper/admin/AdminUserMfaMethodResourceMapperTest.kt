package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.mfa.TotpEnrollment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class AdminUserMfaMethodResourceMapperTest {

    private val mapper = AdminUserMfaMethodResourceMapper()

    @Test
    fun `toResource - Publishes a TOTP enrollment as an MFA method of type totp`() {
        val enrollment = TotpEnrollment(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            secret = ByteArray(20),
            creationDate = LocalDateTime.of(2026, 1, 1, 10, 0),
            confirmedDate = LocalDateTime.of(2026, 1, 1, 10, 1)
        )

        val resource = mapper.toResource(enrollment)

        assertEquals(enrollment.id, resource.mfaId)
        assertEquals("totp", resource.type)
        assertEquals(enrollment.confirmedDate, resource.registeredAt)
    }
}
