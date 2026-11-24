package com.sympauthy.business.flow.model

import com.sympauthy.business.password.model.HashedPassword
import java.time.LocalDateTime
import java.util.*

/**
 * How far an [InteractiveFlowPurpose.PASSWORD_RESET] gate has got, attached to an [InteractiveFlowSession].
 *
 * Keyed by [sessionId] and read through
 * [com.sympauthy.business.flow.reset.manager.InteractiveFlowSessionPasswordResetManager], never carried on the
 * session itself.
 */
data class InteractiveFlowSessionPasswordReset(
    /**
     * Identifier of the [InteractiveFlowSession] this reset is attached to.
     */
    val sessionId: UUID,

    /**
     * The identifier the end-user typed, as they typed it. It is shown back to them on the code page whether or
     * not an account holds it.
     */
    val login: String,

    /**
     * Identifier of the account a code was sent to for [login], or null where none was: no account holds it,
     * or the one that does may not be reset. A code proves only this account.
     */
    val userId: UUID?,

    /**
     * When the end-user last asked for a code, which is when a code or a notice last went out where one did.
     * How soon another may be asked for is counted from it, whatever was sent.
     */
    val requestedDate: LocalDateTime,

    /**
     * When a code proved [userId], or null until one has.
     */
    val codeProvenDate: LocalDateTime? = null,

    /**
     * The new password the end-user chose, hashed, or null until they have. It replaces the account's own only
     * when the session completes.
     */
    val newPassword: HashedPassword? = null,

    /**
     * When the end-user chose [newPassword], or null until they have.
     */
    val passwordChosenDate: LocalDateTime? = null
)
