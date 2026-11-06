package com.sympauthy.business.model.user

/**
 * An account named rather than merely identified, as a record held for a person embeds it.
 *
 * [identifierClaims] is what this deployment identifies a person by and nothing else — a reader of such a
 * record needs to know whose it is, and the rest of the claim set is what `admin:users:read` is for.
 */
data class UserWithIdentifierClaims(
    val user: User,
    val identifierClaims: List<CollectedClaim>
)
