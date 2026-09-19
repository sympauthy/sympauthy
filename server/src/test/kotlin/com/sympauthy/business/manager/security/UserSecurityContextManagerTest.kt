package com.sympauthy.business.manager.security

import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.observedRequestOf
import com.sympauthy.business.model.security.securityContextKey
import com.sympauthy.data.model.InteractiveFlowSessionSecurityContextEntity
import com.sympauthy.data.model.UserSecurityContextEntity
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import com.sympauthy.data.repository.UserSecurityContextRepository
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

/**
 * Recording a place against a session, and folding the one a credential was proven at into a person
 * once their flow completes.
 *
 * The fold is driven by a proven row rather than by the session carrying a user, so the cases that
 * matter most here are the ones where a session holds places nothing was proven at.
 */
@ExtendWith(MockKExtension::class)
class UserSecurityContextManagerTest {

    private val userManager = mockk<UserManager>(relaxed = true)
    private val sessionRepository = mockk<InteractiveFlowSessionSecurityContextRepository>(relaxed = true)
    private val contextRepository = mockk<UserSecurityContextRepository>(relaxed = true)
    private val manager = UserSecurityContextManager(userManager, sessionRepository, contextRepository)

    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `observe - Records where the request came from`() = runTest {
        val observed = observedRequestOf(
            ipAddress = "203.0.113.7",
            userAgent = "Mozilla/5.0",
            geo = SecurityContextGeo("FR", null, null, "Lyon", null, "Europe/Paris")
        )
        val recorded = observation()

        manager.observe(sessionId, observed)

        assertEquals(sessionId, recorded.captured.sessionId)
        assertEquals(observed.securityContextKey().fingerprint, recorded.captured.fingerprint)
        assertEquals("203.0.113.7", recorded.captured.ip)
        assertEquals("Mozilla/5.0", recorded.captured.userAgent)
        assertEquals("FR", recorded.captured.countryCode)
        assertEquals("Lyon", recorded.captured.city)
        assertEquals("Europe/Paris", recorded.captured.timeZone)
    }

    /** One request is one row: the helper recorded the place, and this only stamps it. */
    @Test
    fun `markProven - Stamps the place the request came from without recording it again`() = runTest {
        val fingerprint = slot<String>()
        coEvery { sessionRepository.markProven(eq(sessionId), capture(fingerprint), any()) } returns 1

        val observed = observedRequestOf(ipAddress = "203.0.113.7")
        manager.markProven(sessionId, observed)

        assertEquals(observed.securityContextKey().fingerprint, fingerprint.captured)
        coVerify(exactly = 0) {
            sessionRepository.observe(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    /** The place was rolled out before the proof landed: the sighting is lost, the flow is not. */
    @Test
    fun `markProven - Does not fail the flow where the session no longer holds that place`() = runTest {
        coEvery { sessionRepository.markProven(any(), any(), any()) } returns 0

        manager.markProven(sessionId, observedRequestOf())
    }

    /**
     * The postal code is read at the boundary and deliberately not kept: it narrows to a street group and
     * nothing reads it, which is the answer `docs/security-context.md` already gives a coordinate pair. An edge
     * that sent nothing else therefore leaves a row with no location at all.
     */
    @Test
    fun `observe - Keeps no location where the only field an edge sent was a postal code`() = runTest {
        val recorded = observation()

        manager.observe(sessionId, observedRequestOf(geo = SecurityContextGeo(null, null, null, null, "69001", null)))

        assertNull(recorded.captured.countryCode)
        assertNull(recorded.captured.regionCode)
        assertNull(recorded.captured.region)
        assertNull(recorded.captured.city)
        assertNull(recorded.captured.timeZone)
        // The value itself reaches no column: nothing on the row carries it under another name.
        assertFalse(listOfNotNull(recorded.captured.ip, recorded.captured.userAgent).any { "69001" in it })
    }

    /** What a session is read for is where it is being driven from now, so the new place wins the room. */
    @Test
    fun `observe - Rolls a place out and records the new one where the session holds as many as it may`() =
        runTest {
            val recorded = observation(0, 1)
            coEvery { sessionRepository.deleteLeastRecentPlace(sessionId) } returns 1

            manager.observe(sessionId, observedRequestOf(ipAddress = "203.0.113.7"))

            coVerifyOrder {
                sessionRepository.observe(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
                sessionRepository.deleteLeastRecentPlace(sessionId)
                sessionRepository.observe(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            assertEquals("203.0.113.7", recorded.captured.ip)
        }

    /** A concurrent request took the room this one made: the place is lost, the flow is not. */
    @Test
    fun `observe - Does not fail the flow nor try again when the room it made was taken`() = runTest {
        observation(0, 0)
        coEvery { sessionRepository.deleteLeastRecentPlace(sessionId) } returns 1

        manager.observe(sessionId, observedRequestOf())

        coVerify(exactly = 1) { sessionRepository.deleteLeastRecentPlace(sessionId) }
    }

    @Test
    fun `observe - Does not fail the flow when the row cannot be written`() = runTest {
        coEvery {
            sessionRepository.observe(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } throws IllegalStateException("down")

        manager.observe(sessionId, observedRequestOf())
    }

    @Test
    fun `markProven - Does not fail the flow when the stamp cannot be written`() = runTest {
        coEvery { sessionRepository.markProven(any(), any(), any()) } throws IllegalStateException("down")

        manager.markProven(sessionId, observedRequestOf())
    }

    @Test
    fun `fold - Writes nothing where the session recorded nothing`() = runTest {
        coEvery { sessionRepository.findBySessionId(sessionId) } returns emptyList()

        manager.fold(sessionId, userId)

        coVerify(exactly = 0) { contextRepository.save(any()) }
        coVerify(exactly = 0) {
            contextRepository.updateLastSeenDate(any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) { sessionRepository.deleteBySessionIdIn(any()) }
    }

    /**
     * Anybody holding the session's state writes rows here, so a place nothing was proven at is invisible
     * to the fold — and it is consumed all the same, because the session it described is over.
     */
    @Test
    fun `fold - Writes nothing against the person where no place was proven`() = runTest {
        coEvery { sessionRepository.findBySessionId(sessionId) } returns listOf(row(provenDate = null))

        manager.fold(sessionId, userId)

        coVerify(exactly = 0) { contextRepository.save(any()) }
        coVerify { sessionRepository.deleteBySessionIdIn(listOf(sessionId)) }
    }

    @Test
    fun `fold - Opens a place the person has not been seen at`() = runTest {
        coEvery { sessionRepository.findBySessionId(sessionId) } returns listOf(row())
        coEvery { contextRepository.findByUserIdAndFingerprint(userId, FINGERPRINT) } returns null
        val written = slot<UserSecurityContextEntity>()
        coEvery { contextRepository.save(capture(written)) } answers { written.captured }

        manager.fold(sessionId, userId)

        assertEquals(userId, written.captured.userId)
        assertEquals(FINGERPRINT, written.captured.fingerprint)
        assertEquals(1, written.captured.observationCount)
        assertEquals(written.captured.firstSeenDate, written.captured.lastSeenDate)
        // Dated when the credential was presented, not when the flow happened to finish.
        assertEquals(PROVEN_AT, written.captured.firstSeenDate)
    }

    /** A flow proving a credential twice folds the place the person last proved who they were. */
    @Test
    fun `fold - Takes the place of the latest proof`() = runTest {
        coEvery { sessionRepository.findBySessionId(sessionId) } returns listOf(
            row(fingerprint = "e".repeat(64), provenDate = PROVEN_AT.plusMinutes(5), city = "Paris"),
            row(city = "Lyon"),
            row(fingerprint = "f".repeat(64), provenDate = null, city = "Marseille")
        )
        coEvery { contextRepository.findByUserIdAndFingerprint(userId, any()) } returns null
        val written = slot<UserSecurityContextEntity>()
        coEvery { contextRepository.save(capture(written)) } answers { written.captured }

        manager.fold(sessionId, userId)

        assertEquals("e".repeat(64), written.captured.fingerprint)
        assertEquals("Paris", written.captured.city)
        assertEquals(PROVEN_AT.plusMinutes(5), written.captured.firstSeenDate)
    }

    @Test
    fun `fold - Bumps the count and the location of a place already known`() = runTest {
        val existingId = UUID.randomUUID()
        coEvery { sessionRepository.findBySessionId(sessionId) } returns listOf(row(city = "Paris"))
        coEvery { contextRepository.findByUserIdAndFingerprint(userId, FINGERPRINT) } returns
            existing(existingId, observationCount = 4)
        coEvery {
            contextRepository.updateLastSeenDate(any(), any(), any(), any(), any(), any(), any(), any())
        } returns 1

        manager.fold(sessionId, userId)

        coVerify {
            contextRepository.updateLastSeenDate(
                id = existingId,
                lastSeenDate = any(),
                observationCount = 5,
                countryCode = any(),
                regionCode = any(),
                region = any(),
                city = "Paris",
                timeZone = any()
            )
        }
        coVerify(exactly = 0) { contextRepository.save(any()) }
    }

    /** Consuming them is what makes a second fold do nothing rather than count one sighting twice. */
    @Test
    fun `fold - Consumes every place the session held`() = runTest {
        coEvery { sessionRepository.findBySessionId(sessionId) } returns listOf(
            row(), row(fingerprint = "e".repeat(64), provenDate = null)
        )
        coEvery { contextRepository.findByUserIdAndFingerprint(userId, FINGERPRINT) } returns null

        manager.fold(sessionId, userId)

        coVerify { sessionRepository.deleteBySessionIdIn(listOf(sessionId)) }
    }

    @Test
    fun `fold - Refuses to write against an account still being signed up`() = runTest {
        coEvery { sessionRepository.findBySessionId(sessionId) } returns listOf(row())
        coEvery { userManager.checkPromoted(userId) } throws IllegalStateException("provisional")

        manager.fold(sessionId, userId)

        coVerify(exactly = 0) { contextRepository.save(any()) }
    }

    /**
     * Two completions inserting one place: the loser of the unique index folds again, and the second pass
     * finds the row and updates it.
     */
    @Test
    fun `fold - Folds again as an update when it lost the race to insert`() = runTest {
        val existingId = UUID.randomUUID()
        coEvery { sessionRepository.findBySessionId(sessionId) } returns listOf(row())
        coEvery { contextRepository.findByUserIdAndFingerprint(userId, FINGERPRINT) } returnsMany
            listOf(null, existing(existingId, observationCount = 1))
        coEvery { contextRepository.save(any()) } throws R2dbcDataIntegrityViolationException("duplicate")

        manager.fold(sessionId, userId)

        coVerify {
            contextRepository.updateLastSeenDate(
                id = existingId,
                lastSeenDate = any(),
                observationCount = 2,
                countryCode = any(),
                regionCode = any(),
                region = any(),
                city = any(),
                timeZone = any()
            )
        }
    }

    @Test
    fun `fold - Does not fail the flow when the place cannot be written`() = runTest {
        coEvery { sessionRepository.findBySessionId(sessionId) } throws IllegalStateException("down")

        manager.fold(sessionId, userId)
    }

    /**
     * The upsert's arguments, as a row: the statement takes them apart and every case here is about what
     * would be written rather than about how many parameters that takes.
     */
    private fun observation(vararg recordedRows: Int = intArrayOf(1)): CapturingSlot<Observation> {
        val captured = slot<Observation>()
        var call = 0
        coEvery {
            sessionRepository.observe(
                sessionId = any(),
                fingerprint = any(),
                ip = any(),
                userAgent = any(),
                countryCode = any(),
                regionCode = any(),
                region = any(),
                city = any(),
                timeZone = any(),
                observedDate = any(),
                maxPlaces = any()
            )
        } answers {
            captured.captured = Observation(
                sessionId = firstArg(),
                fingerprint = arg(1),
                ip = arg(2),
                userAgent = arg(3),
                countryCode = arg(4),
                regionCode = arg(5),
                region = arg(6),
                city = arg(7),
                timeZone = arg(8),
                observedDate = arg(9)
            )
            recordedRows.getOrElse(call++) { recordedRows.last() }
        }
        return captured
    }

    private data class Observation(
        val sessionId: UUID,
        val fingerprint: String,
        val ip: String,
        val userAgent: String?,
        val countryCode: String?,
        val regionCode: String?,
        val region: String?,
        val city: String?,
        val timeZone: String?,
        val observedDate: LocalDateTime
    )

    private fun row(
        fingerprint: String = FINGERPRINT,
        provenDate: LocalDateTime? = PROVEN_AT,
        city: String? = null
    ) = InteractiveFlowSessionSecurityContextEntity(
        sessionId = sessionId,
        fingerprint = fingerprint,
        ip = "203.0.113.7",
        userAgent = "Mozilla/5.0",
        city = city,
        firstSeenDate = PROVEN_AT,
        lastSeenDate = PROVEN_AT,
        provenDate = provenDate
    )

    private fun existing(id: UUID, observationCount: Int) = UserSecurityContextEntity(
        userId = userId,
        fingerprint = FINGERPRINT,
        ip = "203.0.113.7",
        userAgent = "Mozilla/5.0",
        firstSeenDate = LocalDateTime.now().minusDays(30),
        lastSeenDate = LocalDateTime.now().minusDays(30),
        observationCount = observationCount
    ).also { it.id = id }

    private companion object {
        val FINGERPRINT = "d".repeat(64)

        /** When the credential was presented, which the fold dates the sighting from. */
        val PROVEN_AT: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
    }
}
