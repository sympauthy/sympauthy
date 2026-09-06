package com.sympauthy.business.mapper

import com.sympauthy.data.model.SecurityContextEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class SecurityContextMapperTest {

    private val mapper = Mappers.getMapper(SecurityContextMapper::class.java)

    @Test
    fun `toSecurityContext - Map every column, the geo among them`() {
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val firstSeen = LocalDateTime.of(2026, 1, 1, 12, 0)
        val lastSeen = LocalDateTime.of(2026, 3, 1, 9, 30)
        val entity = entity(
            id = id,
            userId = userId,
            sessionId = sessionId,
            firstSeenDate = firstSeen,
            lastSeenDate = lastSeen,
            observationCount = 42,
            expirationDate = lastSeen.plusDays(180)
        )

        val context = mapper.toSecurityContext(entity)

        assertEquals(id, context.id)
        assertEquals(userId, context.userId)
        assertEquals(sessionId, context.sessionId)
        assertEquals("fingerprint", context.fingerprint)
        assertEquals("198.51.100.10", context.ip)
        assertEquals("Mozilla/5.0", context.userAgent)
        assertEquals("FR", context.geo.country)
        assertEquals("OCC", context.geo.region)
        assertEquals("Toulouse", context.geo.city)
        assertEquals(firstSeen, context.firstSeenDate)
        assertEquals(lastSeen, context.lastSeenDate)
        assertEquals(42, context.observationCount)
        assertEquals(lastSeen.plusDays(180), context.expirationDate)
    }

    @Test
    fun `toSecurityContext - Map a context nobody is attached to and no proxy placed`() {
        val entity = entity(id = UUID.randomUUID(), userId = null, sessionId = null, geo = null, ip = null)

        val context = mapper.toSecurityContext(entity)

        assertNull(context.userId)
        assertNull(context.sessionId)
        assertNull(context.ip)
        assertNull(context.geo.country)
        assertNull(context.geo.region)
        assertNull(context.geo.city)
    }

    private fun entity(
        id: UUID,
        userId: UUID? = null,
        sessionId: UUID? = null,
        ip: String? = "198.51.100.10",
        geo: String? = "set",
        firstSeenDate: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        lastSeenDate: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        observationCount: Int = 1,
        expirationDate: LocalDateTime = LocalDateTime.of(2026, 1, 2, 12, 0)
    ) = SecurityContextEntity(
        userId = userId,
        sessionId = sessionId,
        fingerprint = "fingerprint",
        ip = ip,
        userAgent = "Mozilla/5.0",
        country = geo?.let { "FR" },
        region = geo?.let { "OCC" },
        city = geo?.let { "Toulouse" },
        firstSeenDate = firstSeenDate,
        lastSeenDate = lastSeenDate,
        observationCount = observationCount,
        expirationDate = expirationDate
    ).also { it.id = id }
}
