package com.sympauthy.business.manager.lock

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*

/**
 * An object two transactions may need to take turns over, named so that both of them name it the same
 * way.
 *
 * The subtypes below are what may be locked, and a caller wanting something else adds one here rather
 * than inventing a string at the call site. [LockManager] is what takes them.
 *
 * A key is a scope and a value, and the value is the one the check it protects compares on: a lock over
 * a spelling that check would not have matched excludes nothing. It hashes to one of [STRIPE_COUNT]
 * rows, so two unrelated keys sometimes share a row and wait for each other — a few milliseconds, and
 * never a wrong answer. What that buys is a lock table the migration creates once: no row is inserted at
 * runtime, so nothing needs an upsert the dialects spell differently, nothing grows by one row per value
 * anyone ever submits, and nothing has to be collected afterwards.
 */
sealed class LockKey(
    private val scope: String,
    private val value: String
) {

    /**
     * An account, for a writer touching more than one of the tables it owns. The promotion of a
     * provisional account and the sweep that collects an abandoned one are the two that do.
     */
    class User(userId: UUID) : LockKey("user", userId.toString())

    /**
     * The value of an identifier claim, held by an account or held by nobody yet.
     *
     * It is the case a constraint cannot express: an end-user signs in with any configured identifier
     * claim, so a value has to be unique across all of them rather than within one column, and the
     * competitor a promotion has to exclude may have no committed row to lock.
     */
    class IdentifierValue(value: String) : LockKey("identifier_value", value)

    /**
     * The row of `object_locks` this key is taken over.
     *
     * SHA-256 rather than [Any.hashCode] because the mapping has to be the same in every instance and in
     * every version: two instances disagreeing about it would take two different rows and exclude
     * nothing. `LockKeyTest` holds it to the values it answers today. The scope and the value are
     * separated by a byte neither of them can contain, so no pair of them hashes as another pair.
     */
    internal val stripe: Int
        get() {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$scope\u0000$value".toByteArray(Charsets.UTF_8))
            return ByteBuffer.wrap(digest).int.mod(STRIPE_COUNT)
        }

    companion object {

        /**
         * The number of rows the migration creating `object_locks` inserts.
         *
         * Fixed for the life of the schema: two instances running different counts map one key to two
         * rows, so raising it is a change every instance has to be stopped for.
         */
        const val STRIPE_COUNT = 64
    }
}
