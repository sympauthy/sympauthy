package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("passwords")
class PasswordEntity(
    val userId: UUID,

    val salt: ByteArray,
    val hashedPassword: ByteArray,

    val creationDate: LocalDateTime = LocalDateTime.now(),
    val expirationDate: LocalDateTime?,

    override val sessionId: UUID?
) : SessionScoped {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
