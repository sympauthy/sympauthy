package com.sympauthy.business.manager.securitycontext

import com.sympauthy.business.manager.securitycontext.edge.CloudflareEdgeProviderProfile
import com.sympauthy.business.manager.securitycontext.edge.NoneEdgeProviderProfile
import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.SecurityContextField
import com.sympauthy.business.model.securitycontext.SecurityContextField.CITY
import com.sympauthy.business.model.securitycontext.SecurityContextField.CLIENT_IP
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.business.model.securitycontext.ObservedSecurityContext
import com.sympauthy.business.model.securitycontext.SecurityContextGeo
import com.sympauthy.business.model.securitycontext.fingerprint
import com.sympauthy.business.model.securitycontext.observedRequestOf
import com.sympauthy.business.mapper.SecurityContextMapper
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.data.model.SecurityContextEntity
import com.sympauthy.data.repository.SecurityContextRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mapstruct.factory.Mappers
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class SecurityContextManagerTest {

    @MockK
    lateinit var securityContexts: SecurityContextRepository

    private val unknownRetention: Duration = Duration.ofHours(24)

    private val knownRetention: Duration = Duration.ofDays(180)

    @Test
    fun `getObservedSecurityContext - Record the socket peer where the deployment named no proxy`() =
        runTest {
            val request = observedRequestOf(
                peer = "198.51.100.10",
                "CF-Connecting-IP" to "1.2.3.4",
                "X-Forwarded-For" to "1.2.3.4",
                "User-Agent" to "Mozilla/5.0"
            )

            val observed = managerOf().getObservedSecurityContext(request)

            assertEquals("198.51.100.10", observed.ip)
            assertEquals("Mozilla/5.0", observed.userAgent)
            assertNull(observed.geo.country)
            assertNull(observed.geo.region)
            assertNull(observed.geo.city)
        }

    @Test
    fun `getObservedSecurityContext - Answer no user agent where the caller sent none`() = runTest {
        val request = observedRequestOf(peer = "198.51.100.10")

        val observed = managerOf().getObservedSecurityContext(request)

        assertNull(observed.userAgent)
    }

    @Test
    fun `getObservedSecurityContext - Read the bound header, and leave the other fields on the profile`() =
        runTest {
            val request = observedRequestOf(
                peer = "10.0.0.1",
                "CF-Connecting-IP" to "198.51.100.10",
                "CF-IPCountry" to "FR",
                "cf-ipcity" to "Toulouse",
                "X-My-Proxy-City" to "Blagnac"
            )

            val observed = managerOf(
                profile = CloudflareEdgeProviderProfile(),
                headers = mapOf(CITY to "X-My-Proxy-City")
            ).getObservedSecurityContext(request)

            assertEquals("198.51.100.10", observed.ip)
            assertEquals("FR", observed.geo.country)
            assertEquals("Blagnac", observed.geo.city)
        }

    @Test
    fun `getObservedSecurityContext - Read an overridden header as it stands`() = runTest {
        val request = observedRequestOf(peer = "10.0.0.1", "X-Forwarded-For" to "1.2.3.4, 198.51.100.10")

        val observed = managerOf(headers = mapOf(CLIENT_IP to "X-Forwarded-For"))
            .getObservedSecurityContext(request)

        assertEquals("1.2.3.4, 198.51.100.10", observed.ip)
    }

    @Test
    fun `getObservedSecurityContext - Read the header a deployment named where it named no proxy`() = runTest {
        val request = observedRequestOf(peer = "10.0.0.1", "X-My-Proxy-IP" to "198.51.100.10")

        val observed = managerOf(headers = mapOf(CLIENT_IP to "X-My-Proxy-IP"))
            .getObservedSecurityContext(request)

        assertEquals("198.51.100.10", observed.ip)
    }

    @Test
    fun `getObservedSecurityContext - Answer null where the overridden header did not arrive`() = runTest {
        val request = observedRequestOf(peer = "10.0.0.1", "CF-Connecting-IP" to "198.51.100.10")

        val observed = managerOf(
            profile = CloudflareEdgeProviderProfile(),
            headers = mapOf(CLIENT_IP to "X-My-Proxy-IP")
        ).getObservedSecurityContext(request)

        assertNull(observed.ip)
    }

    @Test
    fun `recordObservation - Write a place neither the session nor the user has been seen in`() = runTest {
        val observed = observedIn("198.51.100.10")
        coEvery { securityContexts.save(any()) } answers { savedWithAnId() }

        val context = managerOf().recordObservation(observed)

        assertEquals("198.51.100.10", context.ip)
        assertEquals("Mozilla/5.0", context.userAgent)
        assertEquals("FR", context.geo.country)
        assertEquals(observed.fingerprint, context.fingerprint)
        assertEquals(1, context.observationCount)
        assertEquals(context.firstSeenDate, context.lastSeenDate)
        assertEquals(context.lastSeenDate.plus(unknownRetention), context.expirationDate)
        assertNull(context.userId)
    }

    @Test
    fun `recordObservation - Move the sighting on for a place the session carries`() = runTest {
        val observed = observedIn("198.51.100.10")
        val known = contextEntity(fingerprint = observed.fingerprint, observationCount = 3)
        coEvery { securityContexts.findByIdIn(listOf(known.id!!)) } returns listOf(known)
        coEvery { securityContexts.updateLastSeenDate(known.id!!, any(), any(), any()) } returns 1

        val context = managerOf().recordObservation(observed, knownContextIds = listOf(known.id!!))

        assertEquals(known.id, context.id)
        assertEquals(4, context.observationCount)
        assertTrue(context.lastSeenDate.isAfter(known.lastSeenDate))
        coVerify(exactly = 1) {
            securityContexts.updateLastSeenDate(known.id!!, context.lastSeenDate, 4, context.expirationDate)
        }
        coVerify(exactly = 0) { securityContexts.save(any()) }
    }

    @Test
    fun `recordObservation - Reuse the place the user has already been seen in`() = runTest {
        val userId = UUID.randomUUID()
        val observed = observedIn("198.51.100.10")
        val theirs = contextEntity(fingerprint = observed.fingerprint, userId = userId, observationCount = 7)
        coEvery {
            securityContexts.findFirstByUserIdAndFingerprintOrderByFirstSeenDate(userId, observed.fingerprint)
        } returns theirs
        coEvery { securityContexts.updateLastSeenDate(theirs.id!!, any(), any(), any()) } returns 1

        val context = managerOf().recordObservation(observed, userId = userId)

        assertEquals(theirs.id, context.id)
        assertEquals(8, context.observationCount)
        assertEquals(context.lastSeenDate.plus(knownRetention), context.expirationDate)
        coVerify(exactly = 0) { securityContexts.save(any()) }
    }

    @Test
    fun `recordObservation - Attach a place first seen after a sign-in to the user`() = runTest {
        val userId = UUID.randomUUID()
        val observed = observedIn("198.51.100.10")
        coEvery {
            securityContexts.findFirstByUserIdAndFingerprintOrderByFirstSeenDate(userId, observed.fingerprint)
        } returns null
        coEvery { securityContexts.save(any()) } answers { savedWithAnId() }

        val context = managerOf().recordObservation(observed, userId = userId)

        assertEquals(userId, context.userId)
        assertEquals(context.lastSeenDate.plus(knownRetention), context.expirationDate)
    }

    @Test
    fun `promoteToUser - Attach a place nobody was attached to`() = runTest {
        val userId = UUID.randomUUID()
        val promoted = contextEntity(fingerprint = "unattached")
        coEvery { securityContexts.findByIdIn(listOf(promoted.id!!)) } returns listOf(promoted)
        coEvery {
            securityContexts.findFirstByUserIdAndFingerprintOrderByFirstSeenDate(userId, "unattached")
        } returns null
        coEvery { securityContexts.updateUserId(promoted.id!!, userId, any()) } returns 1

        val merged = managerOf().promoteToUser(listOf(promoted.id!!), userId)

        assertEquals(emptyMap<UUID, UUID>(), merged)
        coVerify(exactly = 1) {
            securityContexts.updateUserId(
                promoted.id!!,
                userId,
                promoted.lastSeenDate.plus(knownRetention)
            )
        }
    }

    @Test
    fun `promoteToUser - Absorb a place already the user's and name the survivor`() = runTest {
        val userId = UUID.randomUUID()
        val promoted = contextEntity(
            fingerprint = "same-place",
            firstSeenDate = BASE_DATE.plusDays(2),
            lastSeenDate = BASE_DATE.plusDays(5),
            observationCount = 2
        )
        val theirs = contextEntity(
            fingerprint = "same-place",
            userId = userId,
            firstSeenDate = BASE_DATE,
            lastSeenDate = BASE_DATE.plusDays(3),
            observationCount = 7
        )
        coEvery { securityContexts.findByIdIn(listOf(promoted.id!!)) } returns listOf(promoted)
        coEvery {
            securityContexts.findFirstByUserIdAndFingerprintOrderByFirstSeenDate(userId, "same-place")
        } returns theirs
        coEvery { securityContexts.updateFirstSeenDate(theirs.id!!, any(), any(), any(), any()) } returns 1
        coEvery { securityContexts.deleteByIdIn(listOf(promoted.id!!)) } returns 1

        val merged = managerOf().promoteToUser(listOf(promoted.id!!), userId)

        assertEquals(mapOf(promoted.id!! to theirs.id!!), merged)
        coVerify(exactly = 1) {
            securityContexts.updateFirstSeenDate(
                theirs.id!!,
                BASE_DATE,
                BASE_DATE.plusDays(5),
                9,
                BASE_DATE.plusDays(5).plus(knownRetention)
            )
        }
        coVerify(exactly = 1) { securityContexts.deleteByIdIn(listOf(promoted.id!!)) }
    }

    @Test
    fun `promoteToUser - Leave a place already attached to somebody alone`() = runTest {
        val userId = UUID.randomUUID()
        val attached = contextEntity(fingerprint = "theirs-already", userId = UUID.randomUUID())
        coEvery { securityContexts.findByIdIn(listOf(attached.id!!)) } returns listOf(attached)

        val merged = managerOf().promoteToUser(listOf(attached.id!!), userId)

        assertEquals(emptyMap<UUID, UUID>(), merged)
        coVerify(exactly = 0) { securityContexts.updateUserId(any(), any(), any()) }
    }

    @Test
    fun `promoteToUser - Ask nothing of a session that was seen in no place`() = runTest {
        val merged = unconfiguredManager().promoteToUser(emptyList(), UUID.randomUUID())

        assertEquals(emptyMap<UUID, UUID>(), merged)
        coVerify(exactly = 0) { securityContexts.findByIdIn(any()) }
    }

    @Test
    fun `markReviewed - Record what the client answered about the place`() = runTest {
        val context = securityContext()
        coEvery {
            securityContexts.updateLastDecision(context.id, "REVOKE_SESSION", any())
        } returns 1

        val reviewed = unconfiguredManager().markReviewed(context, AccessReviewDecision.REVOKE_SESSION)

        assertEquals(AccessReviewDecision.REVOKE_SESSION, reviewed.lastDecision)
        coVerify(exactly = 1) {
            securityContexts.updateLastDecision(context.id, "REVOKE_SESSION", reviewed.lastDecisionDate!!)
        }
    }

    @Test
    fun `listPastContexts - Answer the places the person was seen in before this one`() = runTest {
        val userId = UUID.randomUUID()
        val current = UUID.randomUUID()
        val older = contextEntity(fingerprint = "older", userId = userId)
        coEvery { securityContexts.findPastByUserId(userId, current, 1) } returns listOf(older)

        val past = unconfiguredManager().listPastContexts(userId, limit = 1, excluding = current)

        assertEquals(listOf(older.id), past.map { it.id })
    }

    @Test
    fun `listPastContexts - Answer nothing where the deployment asked for no past at all`() = runTest {
        val past = unconfiguredManager()
            .listPastContexts(UUID.randomUUID(), limit = 0, excluding = UUID.randomUUID())

        assertEquals(emptyList<Any>(), past)
        coVerify(exactly = 0) { securityContexts.findPastByUserId(any(), any(), any()) }
    }

    private fun securityContext() = Mappers.getMapper(SecurityContextMapper::class.java)
        .toSecurityContext(contextEntity(fingerprint = "reviewed"))

    /**
     * A manager whose configuration is left unstubbed, for what reads none of it: a retention would
     * fail the strict mock, so reaching the assertion is the proof that none was needed.
     */
    private fun unconfiguredManager() = SecurityContextManager(
        mockk(),
        securityContexts,
        Mappers.getMapper(SecurityContextMapper::class.java)
    )

    private fun observedIn(ip: String) = ObservedSecurityContext(
        ip = ip,
        userAgent = "Mozilla/5.0",
        geo = SecurityContextGeo(country = "FR", region = "OCC", city = "Toulouse")
    )

    private fun contextEntity(
        fingerprint: String,
        userId: UUID? = null,
        firstSeenDate: LocalDateTime = BASE_DATE,
        lastSeenDate: LocalDateTime = BASE_DATE,
        observationCount: Int = 1
    ) = SecurityContextEntity(
        userId = userId,
        fingerprint = fingerprint,
        ip = "198.51.100.10",
        userAgent = "Mozilla/5.0",
        country = "FR",
        region = "OCC",
        city = "Toulouse",
        firstSeenDate = firstSeenDate,
        lastSeenDate = lastSeenDate,
        observationCount = observationCount,
        expirationDate = lastSeenDate.plus(unknownRetention)
    ).also { it.id = UUID.randomUUID() }

    private fun io.mockk.MockKAnswerScope<SecurityContextEntity, SecurityContextEntity>.savedWithAnId() =
        firstArg<SecurityContextEntity>().also { it.id = UUID.randomUUID() }

    private companion object {
        val BASE_DATE: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)
    }

    private fun managerOf(
        profile: EdgeProviderProfile = NoneEdgeProviderProfile(),
        headers: Map<SecurityContextField, String> = emptyMap()
    ): SecurityContextManager {
        val advancedConfig = mockk<EnabledAdvancedConfig>()
        every { advancedConfig.securityContext } returns SecurityContextConfig(
            profile = profile,
            headers = headers,
            unknownRetention = unknownRetention,
            knownRetention = knownRetention
        )
        return SecurityContextManager(
            advancedConfig,
            securityContexts,
            Mappers.getMapper(SecurityContextMapper::class.java)
        )
    }
}
