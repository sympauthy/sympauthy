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
     * An account, for a writer touching more than one of the tables it owns.
     *
     * It names what may be locked rather than who locks it. The abandoned-account sweep is the other
     * writer of those tables and takes nothing: it is a batch, which is what a claim exists for and what
     * "a lock names a bounded set of keys" refuses, and it re-asserts the provisionality its select
     * selected by instead.
     */
    class User(userId: UUID) : LockKey("user", userId.toString())

    /**
     * The value of an identifier claim, held by an account or held by nobody yet.
     *
     * It is the case a constraint cannot express: an end-user signs in with any configured identifier
     * claim, so a value has to be unique across all of them rather than within one column, and the
     * competitor a promotion has to exclude may have no committed row to lock.
     *
     * Two writers make one committed: the promotion of a provisional account, and an invitation applying
     * its pre-assigned claims to an account a provider merge resolved rather than to one the flow created.
     * Neither holds a row the other could have waited on — the promotion's rows are invisible to a committed
     * reader, and a value nobody holds yet has no row at all — so nothing but a key both name serialises
     * them.
     *
     * A write to a provisional account takes no key. Its identifier is not one yet: two sign-ups may hold a
     * value at the same time, and the promotion is where that is settled.
     *
     * The value is the one `collected_claims` holds — what [com.sympauthy.business.mapper.ClaimValueMapper]
     * wrote, quotes and all — because that is the spelling the check compares on. A caller holding the
     * business value maps it before naming it here.
     */
    class IdentifierValue(value: String) : LockKey("identifier_value", value)

    /**
     * A third-party identity, named by the provider asserting it and the subject it carries.
     *
     * Three writers commit a link over it: the promotion of a provisional account, the provider-link flow
     * and the establisher merging a provider into an existing account. Two of them may hold no row the
     * others could have locked — a link written against no account yet exists nowhere to be waited on — so
     * nothing but a key all three name serialises them.
     *
     * The unique index PostgreSQL carries over `(provider_id, subject)` is the backstop
     * `docs/database-standard.md` asks for and not the rule: it is partial, because two provisional links
     * may share a subject by the same design that lets two sign-ups share an address, and H2 spells no
     * partial index at all.
     *
     * The two halves are joined by the byte the digest already separates the scope with, so no pair of
     * them collides with another pair.
     */
    class ProviderSubject(providerId: String, subject: String) :
        LockKey("provider_subject", "$providerId\u0000$subject")

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
