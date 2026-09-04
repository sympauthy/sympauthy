package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.MailQueueEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/**
 * The queued mail, whose `parameters` is the one `json` column the model declares non-null, and whose
 * reader joins two conditions in one derived name — a mail with no expiry is pending as surely as one
 * whose expiry is still ahead.
 */
class MailQueueRepositoryTest {

    private val receiver = "mail-queue-repository-test@example.org"
    private val otherReceiver = "mail-queue-repository-test-other@example.org"

    @ParameterizedTest(name = "save - Round-trips the parameter map on {0}")
    @EnumSource(Database::class)
    fun `save - Round-trips the parameter map`(database: Database) = withFixture(database) {
        val mails = repository<MailQueueRepository>()
        val id = saveMail(parameters = mapOf("code" to "123456", "name" to "Ada"))

        val stored = mails.findById(id)

        assertNotNull(stored)
        assertEquals(mapOf("code" to "123456", "name" to "Ada"), stored!!.parameters)
        assertEquals("validation-code", stored.template)
        assertEquals("en", stored.locale)
        assertEquals(BASE_DATE, stored.creationDate)
    }

    /** The column is non-null, so an empty map has to survive as an empty map rather than as a null. */
    @ParameterizedTest(name = "save - Round-trips an empty parameter map on {0}")
    @EnumSource(Database::class)
    fun `save - Round-trips an empty parameter map`(database: Database) = withFixture(database) {
        val mails = repository<MailQueueRepository>()
        val id = saveMail(parameters = emptyMap())

        val stored = mails.findById(id)

        assertNotNull(stored)
        assertEquals(emptyMap<String, String>(), stored!!.parameters)
    }

    @ParameterizedTest(name = "findByExpirationDateIsNullOrExpirationDateAfter - Keeps the pending mail on {0}")
    @EnumSource(Database::class)
    fun `findByExpirationDateIsNullOrExpirationDateAfter - Keeps the mail that has not expired`(
        database: Database
    ) = withFixture(database) {
        val mails = repository<MailQueueRepository>()
        val neverExpires = saveMail(expirationDate = null)
        val stillPending = saveMail(expirationDate = BASE_DATE.plusDays(2))
        val expired = saveMail(expirationDate = BASE_DATE.minusDays(2))

        val found = mails.findByExpirationDateIsNullOrExpirationDateAfter(BASE_DATE)
            .filter { it.receiver == receiver }
            .map { it.id!! }

        assertTrue(found.contains(neverExpires))
        assertTrue(found.contains(stillPending))
        assertFalse(found.contains(expired))
    }

    @ParameterizedTest(name = "deleteByExpirationDateBefore - Removes the expired mail and counts it on {0}")
    @EnumSource(Database::class)
    fun `deleteByExpirationDateBefore - Removes the expired mail and counts it`(database: Database) =
        withFixture(database) {
            val mails = repository<MailQueueRepository>()
            val expired = saveMail(expirationDate = BASE_DATE.minusDays(2), receiver = otherReceiver)
            val neverExpires = saveMail(expirationDate = null, receiver = otherReceiver)
            val stillPending = saveMail(expirationDate = BASE_DATE.plusDays(2), receiver = otherReceiver)

            val count = mails.deleteByExpirationDateBefore(BASE_DATE)

            assertEquals(1, count)
            assertNull(mails.findById(expired))
            assertNotNull(mails.findById(neverExpires))
            assertNotNull(mails.findById(stillPending))
        }

    private suspend fun RepositoryFixture.saveMail(
        parameters: Map<String, String> = mapOf("code" to "123456"),
        expirationDate: LocalDateTime? = null,
        receiver: String = this@MailQueueRepositoryTest.receiver
    ): UUID {
        val mails = repository<MailQueueRepository>()
        return mails.save(
            MailQueueEntity(
                template = "validation-code",
                locale = "en",
                receiver = receiver,
                subjectKey = "mail.validation-code.subject",
                parameters = parameters,
                creationDate = BASE_DATE,
                expirationDate = expirationDate
            )
        ).id!!.also { id -> deleteOnEnd { mails.deleteById(id) } }
    }
}
