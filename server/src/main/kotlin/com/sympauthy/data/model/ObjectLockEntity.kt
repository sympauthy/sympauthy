package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable

/**
 * One stripe of the lock table: a row that exists so that a transaction can take it, and that carries
 * nothing else.
 *
 * The rows are the whole table and the migration creates every one of them, so nothing here is ever
 * saved, updated or deleted. [com.sympauthy.business.manager.lock.LockKey] is what maps an object to
 * one of them, and [com.sympauthy.data.repository.ObjectLockRepository] is the only reader.
 */
@Serdeable
@MappedEntity("object_locks")
class ObjectLockEntity {

    @Id
    var id: Int? = null
}
