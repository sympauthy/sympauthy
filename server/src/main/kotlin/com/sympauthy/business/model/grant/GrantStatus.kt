package com.sympauthy.business.model.grant

/**
 * Where a grant this server still holds stands, read at one moment.
 *
 * The four are exclusive, and they are decided in the order they are declared below `LIVE`. See
 * `docs/design/grants.md`.
 */
enum class GrantStatus {
    /**
     * Not revoked, not past its lifetime, and holding a token that has not expired or a code still to be
     * exchanged: what a revocation made now would reach.
     */
    LIVE,

    /** Revoked, by whichever revocation came first. */
    REVOKED,

    /** Past its lifetime. */
    EXPIRED,

    /**
     * Neither revoked nor past its lifetime, but left with no token that has not expired and no code still to
     * be exchanged.
     */
    LAPSED
}

/**
 * A grant, and where it stands at the moment it was read.
 *
 * The status is not the grant's own: whether a grant has lapsed is read off the tokens and the code under it,
 * which the [Grant] does not carry.
 */
data class GrantWithStatus(
    val grant: Grant,
    val status: GrantStatus
)
