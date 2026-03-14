package com.sympauthy.business.model.oauth2

import com.sympauthy.business.model.MaybeExpirable
import java.time.LocalDateTime
import java.util.*

data class AuthenticationToken(
    val id: UUID,
    val type: AuthenticationTokenType,
    /**
     * User ID associated with this token.
     * This can be null for tokens issued via client credentials flow (machine-to-machine).
     */
    val userId: UUID?,
    val clientId: String,
    val scopes: List<String>,
    /**
     * Identifies all the tokens generated during a "session" of the end-user:
     * - the "session" starts when the end-user attempts authorization flow.
     * - until the end-user manually logs out.
     * - or until all tokens associated to the "session" expired.
     *
     * All refreshed tokens will carry the same identifier as the on they are refresh from.
     * This allows to revoke all tokens associated to the "session" when the user tries to log-out.
     *
     * This can be null for tokens issued via client credentials flow.
     */
    val authorizeAttemptId: UUID?,
    /**
     * The OAuth2 grant type used to generate this token.
     * Examples: "authorization_code", "refresh_token", "client_credentials"
     */
    val grantType: String,

    /** Date and time at which this token was revoked, or null if still active. */
    val revokedAt: LocalDateTime?,
    /** Actor who revoked this token, or null if still active. */
    val revokedBy: TokenRevokedBy?,
    /** Identifier of the user or admin who revoked this token, or null if still active or not applicable. */
    val revokedById: UUID?,
    val issueDate: LocalDateTime,
    override val expirationDate: LocalDateTime?
): MaybeExpirable {
    /** Whether this token has been revoked. */
    val revoked: Boolean get() = revokedAt != null
}
