package com.sympauthy.data.repository

import com.sympauthy.data.model.ObjectLockEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository

/**
 * Repository for [ObjectLockEntity], the stripes a named lock is taken over.
 *
 * It declares one query and no writer: the rows come from the migration and none of them ever changes.
 */
interface ObjectLockRepository : CoroutineCrudRepository<ObjectLockEntity, Int> {

    /**
     * Take the row lock on the stripe [id], waiting for whoever holds it, and answer the stripe.
     *
     * The lock belongs to the transaction this runs in and is released when it ends, so a caller outside
     * a transaction holds it only for the length of this statement.
     * [com.sympauthy.business.manager.lock.LockManager] is what makes that a transaction worth having.
     *
     * One stripe per call rather than an `IN` list over all of them: the caller takes them in ascending
     * order, which is what makes two overlapping sets of keys deadlock-free, and an ordering the caller
     * performs is one no query plan can reorder.
     */
    @Query("SELECT id FROM object_locks WHERE id = :id FOR UPDATE")
    suspend fun lock(id: Int): Int?
}
