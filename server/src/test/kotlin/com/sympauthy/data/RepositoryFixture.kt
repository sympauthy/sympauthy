package com.sympauthy.data

import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.UserRepository
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import java.util.*

/** The instant every fixture dates its rows from, so no assertion turns on the clock. */
val BASE_DATE: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

/**
 * Runs [block] against [database], deleting the rows it created once it ends.
 *
 * A repository test seeds inside its test rather than in a `@BeforeEach`, which cannot see the
 * parameter naming the database.
 */
fun withFixture(database: Database, block: suspend RepositoryFixture.() -> Unit) {
    val fixture = RepositoryFixture(database)
    runTest {
        try {
            fixture.block()
        } finally {
            fixture.deleteCreatedRows()
        }
    }
}

/**
 * The rows one test created, and the parents it needed to create them.
 *
 * Deletion runs in reverse order of registration, so a row registered after the row it references is
 * removed before it and no foreign key is left dangling.
 */
class RepositoryFixture(val database: Database) {

    /**
     * Resolved here rather than on first use: it starts the context, which for PostgreSQL pulls an
     * image and migrates a schema, and neither belongs inside the coroutine test's timeout.
     */
    private val users = database.bean<UserRepository>()

    private val deletions = ArrayDeque<suspend () -> Unit>()

    inline fun <reified T : Any> repository(): T = database.bean()

    /** Registers [delete] to run when the test ends, ahead of everything registered before it. */
    fun deleteOnEnd(delete: suspend () -> Unit) = deletions.addFirst(delete)

    /** Saves a user to hang rows off, and returns the identifier the database generated. */
    suspend fun newUser(status: String = "enabled"): UUID = users
        .save(UserEntity(status = status, creationDate = BASE_DATE))
        .id!!
        .also { id -> deleteOnEnd { users.deleteById(id) } }

    suspend fun deleteCreatedRows() = deletions.forEach { it() }
}
