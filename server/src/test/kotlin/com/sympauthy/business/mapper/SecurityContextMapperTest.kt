package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.data.model.SecurityContextEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class SecurityContextMapperTest {

    private val mapper = Mappers.getMapper(SecurityContextMapper::class.java)

    @Test
    fun `toSecurityContext - Map every column, the geo among them`() {
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val firstSeen = LocalDateTime.of(2026, 1, 1, 12, 0)
        val lastSeen = LocalDateTime.of(2026, 3, 1, 9, 30)
        val entity = entity(
            id = id,
            userId = userId,
            firstSeenDate = firstSeen,
            lastSeenDate = lastSeen,
            observationCount = 42,
            expirationDate = lastSeen.plusDays(180)
        )

        val context = mapper.toSecurityContext(entity)

        assertEquals(id, context.id)
        assertEquals(userId, context.userId)
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
        val entity = entity(id = UUID.randomUUID(), userId = null, geo = null, ip = null)

        val context = mapper.toSecurityContext(entity)

        assertNull(context.userId)
        assertNull(context.ip)
        assertNull(context.geo.country)
        assertNull(context.geo.region)
        assertNull(context.geo.city)
    }

    @Test
    fun `toSecurityContext - Map what the last access review answered`() {
        val entity = entity(id = UUID.randomUUID(), lastDecision = AccessReviewDecision.REVOKE_SESSION.name)

        val context = mapper.toSecurityContext(entity)

        assertEquals(AccessReviewDecision.REVOKE_SESSION, context.lastDecision)
        assertEquals(LocalDateTime.of(2026, 1, 1, 12, 0), context.lastDecisionDate)
    }

    @Test
    fun `toSecurityContext - Map a place no client has reviewed`() {
        val context = mapper.toSecurityContext(entity(id = UUID.randomUUID()))

        assertNull(context.lastDecision)
        assertNull(context.lastDecisionDate)
    }

    @Test
    fun `toSecurityContext - Refuse a decision naming nothing this server can apply`() {
        val entity = entity(id = UUID.randomUUID(), lastDecision = "maybe")

        val exception = assertThrows<BusinessException> { mapper.toSecurityContext(entity) }

        assertEquals("mapper.security_context.invalid_property", exception.detailsId)
    }

    private fun entity(
        id: UUID,
        userId: UUID? = null,
        ip: String? = "198.51.100.10",
        geo: String? = "set",
        firstSeenDate: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        lastSeenDate: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        observationCount: Int = 1,
        expirationDate: LocalDateTime = LocalDateTime.of(2026, 1, 2, 12, 0),
        lastDecision: String? = null
    ) = SecurityContextEntity(
        userId = userId,
        fingerprint = "fingerprint",
        ip = ip,
        userAgent = "Mozilla/5.0",
        country = geo?.let { "FR" },
        region = geo?.let { "OCC" },
        city = geo?.let { "Toulouse" },
        firstSeenDate = firstSeenDate,
        lastSeenDate = lastSeenDate,
        observationCount = observationCount,
        expirationDate = expirationDate,
        lastDecision = lastDecision,
        lastDecisionDate = lastDecision?.let { LocalDateTime.of(2026, 1, 1, 12, 0) }
    ).also { it.id = id }
}
