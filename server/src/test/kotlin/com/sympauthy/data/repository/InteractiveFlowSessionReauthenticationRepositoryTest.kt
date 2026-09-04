package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.InteractiveFlowSessionReauthenticationEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/**
 * The single marker a re-authentication leaves behind — the date the primary credential was proven —
 * keyed by the session it hangs off.
 */
class InteractiveFlowSessionReauthenticationRepositoryTest {

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and leaves the marker unset`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionReauthenticationRepository>()
            val session = newSession()

            saveRecord(session.id!!)

            val stored = records.findById(session.id!!)
            assertNotNull(stored)
            assertEquals(session.id, stored!!.sessionId)
            assertNull(stored.primaryCredentialProvenDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the date the credential was proven`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionReauthenticationRepository>()
        val session = newSession()
        val provenDate = BASE_DATE.plusMinutes(1)

        saveRecord(session.id!!, provenDate)

        assertEquals(provenDate, records.findById(session.id!!)?.primaryCredentialProvenDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Finds the record of the session`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionReauthenticationRepository>()
        val session = newSession()
        val other = newSession()
        saveRecord(session.id!!)

        assertEquals(session.id, records.findBySessionId(session.id!!)?.sessionId)
        assertNull(records.findBySessionId(other.id!!))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the records and counts them`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionReauthenticationRepository>()
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
        primaryCredentialProvenDate: LocalDateTime? = null
    ) {
        val records = repository<InteractiveFlowSessionReauthenticationRepository>()
        records.save(
            InteractiveFlowSessionReauthenticationEntity(
                sessionId = sessionId,
                primaryCredentialProvenDate = primaryCredentialProvenDate
            )
        )
        deleteOnEnd { records.deleteBySessionIdIn(listOf(sessionId)) }
    }
}
