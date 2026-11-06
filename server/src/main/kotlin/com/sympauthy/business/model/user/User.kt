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
    val sessionId: UUID?,
    /**
     * When this person last proved a credential in a flow that went on to complete, whatever that flow was
     * for, or null where nothing has yet. See `docs/design/security.md`.
     */
    val lastAuthenticationDate: LocalDateTime? = null,
    /**
     * When a grant held for this person was last refreshed, or null where none has been. A client acting alone
     * moves this and not [lastAuthenticationDate], which is why the two are not one date.
     */
    val lastRefreshDate: LocalDateTime? = null
)
