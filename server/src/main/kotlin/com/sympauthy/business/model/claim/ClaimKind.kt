package com.sympauthy.business.model.claim

/**
 * Whose a claim's value is: the person's or an application's, declared under `claims.<id>.kind` and
 * held against the [ClaimAcl] by the validator. A generated claim has none. See
 * `docs/design/claims.md`.
 */
enum class ClaimKind {
    /**
     * The person's: a name, an address, a birth date.
     */
    PERSONAL,

    /**
     * An application's, attached to the person: a credit score, a tier, a flag a backend computes.
     */
    APPLICATION
}
