package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_session_password_reset")
class InteractiveFlowSessionPasswordResetEntity(
    @get:Id
    val sessionId: UUID,
    /**
     * The identifier the person typed, as they typed it.
     */
    val login: String,
    /**
     * The account a code was sent to for [login]. Null where none was, whether no account holds it or the one
     * that does may not be reset.
     */
    val userId: UUID?,
    val requestedDate: LocalDateTime,
    val codeProvenDate: LocalDateTime? = null,
    /**
     * The salt and the hash of the new password, both empty until [passwordChosenDate] is set, as
     * `docs/standards/database-standard.md` spells an absent `bytea`.
     */
    val salt: ByteArray = ByteArray(0),
    val hashedPassword: ByteArray = ByteArray(0),
    val passwordChosenDate: LocalDateTime? = null
)
