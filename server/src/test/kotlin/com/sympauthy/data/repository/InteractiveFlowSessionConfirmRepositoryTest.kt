package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.InteractiveFlowSessionConfirmEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/** The action a session asks the end-user to confirm, keyed by the session it hangs off. */
class InteractiveFlowSessionConfirmRepositoryTest {

    private val action = "MFA_ENROLLMENT"
    private val clientId = "interactive-flow-session-confirm-repository-test-client"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and round-trips the row`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionConfirmRepository>()
        val session = newSession()

        saveRecord(session.id!!)

        val stored = records.findById(session.id!!)
        assertNotNull(stored)
        assertEquals(session.id, stored!!.sessionId)
        assertEquals(action, stored.action)
        assertEquals(clientId, stored.clientId)
        assertNull(stored.confirmedDate)
    }

    /** An administrator initiates the action with no client to name, and the column admits that. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips a record naming no client`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionConfirmRepository>()
        val session = newSession()

        saveRecord(session.id!!, clientId = null)

        val stored = records.findById(session.id!!)
        assertNotNull(stored)
        assertNull(stored!!.clientId)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the confirmation date`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionConfirmRepository>()
        val session = newSession()
        val confirmedDate = BASE_DATE.plusMinutes(1)

        saveRecord(session.id!!, confirmedDate = confirmedDate)

        assertEquals(confirmedDate, records.findById(session.id!!)?.confirmedDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Finds the record of the session`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionConfirmRepository>()
        val session = newSession()
        val other = newSession()
        saveRecord(session.id!!)

        assertEquals(session.id, records.findBySessionId(session.id!!)?.sessionId)
        assertNull(records.findBySessionId(other.id!!))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the records and counts them`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionConfirmRepository>()
        val deleted = newSession()
        val kept = newSession()
        saveRecord(deleted.id!!)
        saveRecord(kept.id!!)

        val count = records.deleteBySessionIdIn(listOf(deleted.id!!))

        assertEquals(1, count)
        assertNull(records.findBySessionId(deleted.id!!))
        assertNotNull(records.findBySessionId(kept.id!!))
    }

    private suspend fun RepositoryFixture.saveRecord(
        sessionId: UUID,
        clientId: String? = this@InteractiveFlowSessionConfirmRepositoryTest.clientId,
        confirmedDate: LocalDateTime? = null
    ) {
        val records = repository<InteractiveFlowSessionConfirmRepository>()
        records.save(
            InteractiveFlowSessionConfirmEntity(
                sessionId = sessionId,
                action = action,
                clientId = clientId,
                confirmedDate = confirmedDate
            )
        )
        deleteOnEnd { records.deleteBySessionIdIn(listOf(sessionId)) }
    }
}
