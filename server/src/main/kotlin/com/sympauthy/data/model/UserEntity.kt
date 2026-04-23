package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("users")
data class UserEntity(
    val status: String,
    val creationDate: LocalDateTime
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

