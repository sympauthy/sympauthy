package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.DataType
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("invitations")
class InvitationEntity(
    val audienceId: String,
    val tokenLookupHash: ByteArray,
    val hashedToken: ByteArray,
    val salt: ByteArray,
    val tokenPrefix: String,
    @MappedProperty(type = DataType.JSON)
    val claims: Map<String, String>? = null,
    val note: String? = null,
    val status: String,
    val createdBy: String,
    val createdById: String? = null,
    val consumedByUserId: UUID? = null,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val consumedAt: LocalDateTime? = null,
    val revokedAt: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
