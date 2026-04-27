package com.sympauthy.business.model.oauth2

import java.time.LocalDateTime
import java.util.*

/**
 * The type of actor who revoked a consent.
 */
enum class ConsentRevokedBy {
    /** The consent was revoked by the user themselves. */
    USER,

    /** The consent was revoked by an administrator. */
    ADMIN
}

/**
 * A record of the scopes a user has authorized for a given audience.
 *
 * Only one active (non-revoked) consent may exist per user+audience pair at any time.
 * A consent is considered active when [revokedAt] is null.
 */
data class Consent(
    /** Unique identifier of the consent. */
    val id: UUID,
    /** Identifier of the user who granted the consent. */
    val userId: UUID,
    /** Identifier of the audience the consent applies to. */
    val audienceId: String,
    /** Identifier of the client that originally prompted the consent. */
    val promptedByClientId: String,
    /** List of scope identifiers the user authorized for this client. */
    val scopes: List<String>,
    /** Date and time at which the user granted the consent. */
    val consentedAt: LocalDateTime,
    /** Date and time at which the consent was revoked, or null if still active. */
    val revokedAt: LocalDateTime?,
    /** Actor who revoked the consent, or null if still active. */
    val revokedBy: ConsentRevokedBy?,
    /** Identifier of the user or admin who revoked the consent, or null if still active. */
    val revokedById: UUID?
)
