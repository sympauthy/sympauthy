package com.sympauthy.data.repository

import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.InteractiveFlowSessionProviderEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/** The provider a session authenticates through, keyed by the session it hangs off. */
class InteractiveFlowSessionProviderRepositoryTest {

    private val providerId = "interactive-flow-session-provider-repository-test-provider"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and round-trips the row`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionProviderRepository>()
        val session = newSession()
        val nonceId = UUID.randomUUID()

        saveRecord(session.id!!, nonceId)

        val stored = records.findById(session.id!!)
        assertNotNull(stored)
        assertEquals(session.id, stored!!.sessionId)
        assertEquals(providerId, stored.providerId)
        assertEquals(nonceId, stored.providerNonceJsonWebTokenId)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips a record carrying no nonce`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionProviderRepository>()
        val session = newSession()

        saveRecord(session.id!!, nonceId = null)

        assertNull(records.findById(session.id!!)!!.providerNonceJsonWebTokenId)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Finds the record of the session`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionProviderRepository>()
        val session = newSession()
        val other = newSession()
        saveRecord(session.id!!, UUID.randomUUID())

        assertEquals(session.id, records.findBySessionId(session.id!!)?.sessionId)
        assertNull(records.findBySessionId(other.id!!))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the records and counts them`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionProviderRepository>()
        val deleted = newSession()
        val kept = newSession()
        saveRecord(deleted.id!!, UUID.randomUUID())
        saveRecord(kept.id!!, UUID.randomUUID())

        val count = records.deleteBySessionIdIn(listOf(deleted.id!!))

        assertEquals(1, count)
        assertNull(records.findBySessionId(deleted.id!!))
        assertNotNull(records.findBySessionId(kept.id!!))
    }

    private suspend fun RepositoryFixture.saveRecord(sessionId: UUID, nonceId: UUID?) {
        val records = repository<InteractiveFlowSessionProviderRepository>()
        records.save(
            InteractiveFlowSessionProviderEntity(
                sessionId = sessionId,
                providerId = providerId,
                providerNonceJsonWebTokenId = nonceId
            )
        )
        deleteOnEnd { records.deleteBySessionIdIn(listOf(sessionId)) }
    }
}
