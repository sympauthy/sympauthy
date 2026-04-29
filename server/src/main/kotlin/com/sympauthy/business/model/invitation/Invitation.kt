package com.sympauthy.business.model.invitation

import com.sympauthy.business.model.Expirable
import java.time.LocalDateTime
import java.util.*

data class Invitation(
    val id: UUID,
    val audienceId: String,
    val tokenPrefix: String,
    val claims: Map<String, String>?,
    val note: String?,
    val status: InvitationStatus,
    val createdBy: InvitationCreatedBy,
    val createdById: String?,
    val consumedByUserId: UUID?,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val consumedAt: LocalDateTime?,
    val revokedAt: LocalDateTime?,
) : Expirable {
    override val expirationDate: LocalDateTime get() = expiresAt
}
