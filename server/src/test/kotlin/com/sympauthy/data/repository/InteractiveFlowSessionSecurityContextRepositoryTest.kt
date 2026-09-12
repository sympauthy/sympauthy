package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.InteractiveFlowSessionSecurityContextEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/** Where a session observed its person, held against the session until the flow folds or expires. */
class InteractiveFlowSessionSecurityContextRepositoryTest {

    private val fingerprint = "c".repeat(64)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and round-trips the row`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionSecurityContextRepository>()
        val session = newSession()

        saveRecord(session.id!!, userAgent = "Mozilla/5.0", city = "Lyon")

        val stored = records.findById(session.id!!)
        assertNotNull(stored)
        assertEquals(session.id, stored!!.sessionId)
        assertEquals(fingerprint, stored.fingerprint)
        assertEquals("203.0.113.7", stored.ip)
        assertEquals("Mozilla/5.0", stored.userAgent)
        assertEquals("Lyon", stored.city)
        assertEquals(BASE_DATE, stored.observedDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Admits a request that carried no user agent and no location`(database: Database) =
        withFixture(database) {
            val session = newSession()

            saveRecord(session.id!!)

            val stored = repository<InteractiveFlowSessionSecurityContextRepository>().findById(session.id!!)
            assertNull(stored!!.userAgent)
            assertNull(stored.city)
            assertNull(stored.countryCode)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Answers the record of that session and no other`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()
            val session = newSession()
            val other = newSession()
            saveRecord(session.id!!)

            assertNotNull(records.findBySessionId(session.id!!))
            assertNull(records.findBySessionId(other.id!!))
        }

    /** The primary key is the session, so a flow proving a credential twice keeps the later sighting. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `update - Replaces what an earlier step of the same flow observed`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()
            val session = newSession()
            saveRecord(session.id!!, city = "Lyon")

            records.update(
                InteractiveFlowSessionSecurityContextEntity(
                    sessionId = session.id!!,
                    fingerprint = fingerprint,
                    ip = "203.0.113.8",
                    city = "Paris",
                    observedDate = BASE_DATE.plusMinutes(5)
                )
            )

            val stored = records.findBySessionId(session.id!!)!!
            assertEquals("203.0.113.8", stored.ip)
            assertEquals("Paris", stored.city)
            assertEquals(BASE_DATE.plusMinutes(5), stored.observedDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the records of the sessions it names`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()
            val deleted = newSession()
            val kept = newSession()
            saveRecord(deleted.id!!)
            saveRecord(kept.id!!)

            assertEquals(1, records.deleteBySessionIdIn(listOf(deleted.id!!)))

            assertNull(records.findBySessionId(deleted.id!!))
            assertNotNull(records.findBySessionId(kept.id!!))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Answers zero where a session left no record`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()

            assertEquals(0, records.deleteBySessionIdIn(listOf(newSession().id!!)))
        }

    private suspend fun RepositoryFixture.saveRecord(
        sessionId: UUID,
        userAgent: String? = null,
        city: String? = null
    ) {
        val records = repository<InteractiveFlowSessionSecurityContextRepository>()
        records.save(
            InteractiveFlowSessionSecurityContextEntity(
                sessionId = sessionId,
                fingerprint = fingerprint,
                ip = "203.0.113.7",
                userAgent = userAgent,
                city = city,
                observedDate = BASE_DATE
            )
        )
        deleteOnEnd { records.deleteBySessionIdIn(listOf(sessionId)) }
    }
}
