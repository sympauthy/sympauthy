package com.sympauthy.business.model.user.claim

/**
 * Whose a claim's value is: the person's or an application's.
 *
 * It is declared rather than inferred. The consent scope gates disclosure and says nothing about
 * ownership, and [ClaimOrigin] says whose the claim's *name* is, so neither answers this — which is
 * why `claims.<id>.kind` exists and why the validator holds the [ClaimAcl] to it.
 * `docs/design/claims.md` is where the rule each kind carries is stated.
 *
 * A generated claim is neither, and carries no value of this at all: it is computed rather than
 * collected and written by nobody, so there is no writer for a kind to name. Every other claim has
 * one.
 */
enum class ClaimKind {
    /**
     * The person's: a name, an address, a birth date. Collected from them in the flow or set through
     * their own access token, and never written by a client except where the claim is restricted to
     * that client's own audience.
     */
    PERSONAL,

    /**
     * An application's, attached to the person: a credit score, a tier, a flag a backend computes.
     * Read and written through the client scopes its ACL names, and never typed by the person.
     */
    APPLICATION
}
