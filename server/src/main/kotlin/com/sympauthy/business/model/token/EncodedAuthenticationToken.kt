package com.sympauthy.business.model.token

import com.sympauthy.business.model.MaybeExpirable
import java.time.LocalDateTime
import java.util.*

/**
 * A token this server issued, with the [token] a client presents.
 *
 * Its `toString` leaves [token] out, so that no record built from one carries the token: see
 * `docs/standards/logging-standard.md`.
 */
data class EncodedAuthenticationToken(
    val id: UUID,
    val type: AuthenticationTokenType,
    val token: String,
    val scopes: List<String>,
    val issueDate: LocalDateTime,
    override val expirationDate: LocalDateTime?
) : MaybeExpirable {

    override fun toString() = "EncodedAuthenticationToken(id=$id, type=$type, token=[redacted], scopes=$scopes, " +
        "issueDate=$issueDate, expirationDate=$expirationDate)"
}
