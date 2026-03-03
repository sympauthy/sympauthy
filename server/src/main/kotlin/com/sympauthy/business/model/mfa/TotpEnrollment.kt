package com.sympauthy.business.model.mfa

import java.time.LocalDateTime
import java.util.*

data class TotpEnrollment(
    val id: UUID,
    val userId: UUID,
    val secret: ByteArray,
    val creationDate: LocalDateTime,
    /**
     * Date at which the user confirmed the enrollment by submitting a valid TOTP code.
     * Null means the enrollment has not been confirmed yet.
     */
    val confirmedDate: LocalDateTime?
) {
    val confirmed: Boolean get() = confirmedDate != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TotpEnrollment) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
