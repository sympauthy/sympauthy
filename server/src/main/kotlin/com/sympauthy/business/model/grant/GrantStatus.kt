package com.sympauthy.business.model.grant

/**
 * Where a grant this server still holds stands, read at one moment.
 *
 * The four are exclusive, and they are decided in the order they are declared below `LIVE`: a revoked grant is
 * [REVOKED] whatever else holds of it, because the revocation is what somebody acted on, and a grant past its
 * lifetime is [EXPIRED] rather than [LAPSED], since every token under it has expired by then too. See
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

    /** Past the lifetime `auth.grant.expiration` gave it. */
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
