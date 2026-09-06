package com.sympauthy.business.model.user

import com.sympauthy.data.model.SessionScoped
import java.time.LocalDateTime
import java.util.*

data class User(
    val id: UUID,
    val status: UserStatus,
    val creationDate: LocalDateTime,
    /**
     * Identifier of the interactive flow session this account is still provisional for, or null once it
     * is a permanent account. See [SessionScoped].
     *
     * It is carried on the model rather than left in the row because every row the account owns takes
     * its own session id from here: that is what holds a satellite row and its user to the same
     * visibility without any call site deciding it.
     */
    val sessionId: UUID?
)
