package com.sympauthy.data.repository

import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.InteractiveFlowSessionLinkProviderEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/** The provider a session was opened to link, keyed by the session it hangs off. */
class InteractiveFlowSessionLinkProviderRepositoryTest {

    private val providerId = "interactive-flow-session-link-provider-repository-test-provider"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and round-trips the row`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionLinkProviderRepository>()
        val session = newSession()

        saveRecord(session.id!!)

        val stored = records.findById(session.id!!)
        assertNotNull(stored)
        assertEquals(session.id, stored!!.sessionId)
        assertEquals(providerId, stored.providerId)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Finds the record of the session`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionLinkProviderRepository>()
        val session = newSession()
        val other = newSession()
        saveRecord(session.id!!)

        assertEquals(session.id, records.findBySessionId(session.id!!)?.sessionId)
        assertNull(records.findBySessionId(other.id!!))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the records and counts them`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionLinkProviderRepository>()
        val deleted = newSession()
        val kept = newSession()
        saveRecord(deleted.id!!)
        saveRecord(kept.id!!)

        val count = records.deleteBySessionIdIn(listOf(deleted.id!!))

        assertEquals(1, count)
        assertNull(records.findBySessionId(deleted.id!!))
        assertNotNull(records.findBySessionId(kept.id!!))
    }

    private suspend fun RepositoryFixture.saveRecord(sessionId: UUID) {
        val records = repository<InteractiveFlowSessionLinkProviderRepository>()
        records.save(
            InteractiveFlowSessionLinkProviderEntity(sessionId = sessionId, providerId = providerId)
        )
        deleteOnEnd { records.deleteBySessionIdIn(listOf(sessionId)) }
    }
}
