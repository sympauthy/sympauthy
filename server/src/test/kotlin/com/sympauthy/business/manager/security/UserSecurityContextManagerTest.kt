package com.sympauthy.business.manager.security

import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.observedRequestOf
import com.sympauthy.business.model.security.securityContextKey
import com.sympauthy.data.model.InteractiveFlowSessionSecurityContextEntity
import com.sympauthy.data.model.UserSecurityContextEntity
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import com.sympauthy.data.repository.UserSecurityContextRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
 * Observing a place against a session, and folding it into a person once their flow completes.
 *
 * The fold is driven by the staged row rather than by the session carrying a user, so the cases that
 * matter most here are the ones where nothing was staged and nothing may therefore be written.
 */
@ExtendWith(MockKExtension::class)
class UserSecurityContextManagerTest {

    private val userManager = mockk<UserManager>(relaxed = true)
    private val stagedRepository = mockk<InteractiveFlowSessionSecurityContextRepository>(relaxed = true)
    private val contextRepository = mockk<UserSecurityContextRepository>(relaxed = true)
    private val manager = UserSecurityContextManager(userManager, stagedRepository, contextRepository)

    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `stage - Saves what was observed against the session`() = runTest {
        val observed = observedRequestOf(
            ipAddress = "203.0.113.7",
            userAgent = "Mozilla/5.0",
            geo = SecurityContextGeo("FR", null, null, "Lyon", null, "Europe/Paris")
        )
        coEvery { stagedRepository.findBySessionId(sessionId) } returns null
        val staged = slot<InteractiveFlowSessionSecurityContextEntity>()
        coEvery { stagedRepository.save(capture(staged)) } answers { staged.captured }

        manager.stage(sessionId, observed)

        assertEquals(sessionId, staged.captured.sessionId)
        assertEquals(observed.securityContextKey().fingerprint, staged.captured.fingerprint)
        assertEquals("203.0.113.7", staged.captured.ip)
        assertEquals("Mozilla/5.0", staged.captured.userAgent)
        assertEquals("FR", staged.captured.countryCode)
        assertEquals("Lyon", staged.captured.city)
        assertEquals("Europe/Paris", staged.captured.timeZone)
    }

    /**
     * The postal code is read at the boundary and deliberately not kept: it narrows to a street group and
     * nothing reads it, which is the answer `docs/security.md` already gives a coordinate pair. An edge
     * that sent nothing else therefore leaves a row with no location at all.
     */
    @Test
    fun `stage - Keeps no location where the only field an edge sent was a postal code`() = runTest {
        val observed = observedRequestOf(
            geo = SecurityContextGeo(null, null, null, null, "69001", null)
        )
        coEvery { stagedRepository.findBySessionId(sessionId) } returns null
        val staged = slot<InteractiveFlowSessionSecurityContextEntity>()
        coEvery { stagedRepository.save(capture(staged)) } answers { staged.captured }

        manager.stage(sessionId, observed)

        assertNull(staged.captured.countryCode)
        assertNull(staged.captured.regionCode)
        assertNull(staged.captured.region)
        assertNull(staged.captured.city)
        assertNull(staged.captured.timeZone)
        // The value itself reaches no column: nothing on the row carries it under another name.
        assertFalse(listOfNotNull(staged.captured.ip, staged.captured.userAgent).any { "69001" in it })
    }

    @Test
    fun `stage - Replaces what an earlier step of the same flow observed`() = runTest {
        coEvery { stagedRepository.findBySessionId(sessionId) } returns stagedRow()

        manager.stage(sessionId, observedRequestOf())

        coVerify { stagedRepository.update(any()) }
        coVerify(exactly = 0) { stagedRepository.save(any()) }
    }

    @Test
    fun `stage - Does not fail the flow when the row cannot be written`() = runTest {
        coEvery { stagedRepository.findBySessionId(sessionId) } throws IllegalStateException("down")

        manager.stage(sessionId, observedRequestOf())
    }

    @Test
    fun `fold - Writes nothing where the session staged nothing`() = runTest {
        coEvery { stagedRepository.findBySessionId(sessionId) } returns null

        manager.fold(sessionId, userId)

        coVerify(exactly = 0) { contextRepository.save(any()) }
        coVerify(exactly = 0) {
            contextRepository.updateLastSeenDate(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `fold - Opens a place the person has not been seen at`() = runTest {
        coEvery { stagedRepository.findBySessionId(sessionId) } returns stagedRow()
        coEvery { contextRepository.findByUserIdAndFingerprint(userId, FINGERPRINT) } returns null
        val written = slot<UserSecurityContextEntity>()
        coEvery { contextRepository.save(capture(written)) } answers { written.captured }

        manager.fold(sessionId, userId)

        assertEquals(userId, written.captured.userId)
        assertEquals(FINGERPRINT, written.captured.fingerprint)
        assertEquals(1, written.captured.observationCount)
        assertEquals(written.captured.firstSeenDate, written.captured.lastSeenDate)
        // Dated when the credential was presented, not when the flow happened to finish.
        assertEquals(OBSERVED_AT, written.captured.firstSeenDate)
    }

    @Test
    fun `fold - Bumps the count and the location of a place already known`() = runTest {
        val existingId = UUID.randomUUID()
        coEvery { stagedRepository.findBySessionId(sessionId) } returns stagedRow(city = "Paris")
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

    /** Consuming it is what makes a second fold do nothing rather than count one sighting twice. */
    @Test
    fun `fold - Consumes the row it folded`() = runTest {
        coEvery { stagedRepository.findBySessionId(sessionId) } returns stagedRow()
        coEvery { contextRepository.findByUserIdAndFingerprint(userId, FINGERPRINT) } returns null

        manager.fold(sessionId, userId)

        coVerify { stagedRepository.deleteBySessionIdIn(listOf(sessionId)) }
    }

    @Test
    fun `fold - Refuses to write against an account still being signed up`() = runTest {
        coEvery { stagedRepository.findBySessionId(sessionId) } returns stagedRow()
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
        coEvery { stagedRepository.findBySessionId(sessionId) } returns stagedRow()
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
        coEvery { stagedRepository.findBySessionId(sessionId) } throws IllegalStateException("down")

        manager.fold(sessionId, userId)
    }

    private fun stagedRow(city: String? = null) = InteractiveFlowSessionSecurityContextEntity(
        sessionId = sessionId,
        fingerprint = FINGERPRINT,
        ip = "203.0.113.7",
        userAgent = "Mozilla/5.0",
        city = city,
        observedDate = OBSERVED_AT
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
        val OBSERVED_AT: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
    }
}
