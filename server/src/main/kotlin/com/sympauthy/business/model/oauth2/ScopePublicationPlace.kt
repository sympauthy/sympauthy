package com.sympauthy.business.model.oauth2

/**
 * A place this authorization server publishes a scope in.
 *
 * It holds one value, which is the shape of the answer rather than a set waiting to grow: a scope is a
 * name a client asks for, so the only thing this server does with one beside serving it is say that it
 * exists. It is a set all the same, and it is written `published-in` in the configuration exactly as
 * [a claim's][com.sympauthy.business.model.user.claim.ClaimPublicationPlace] is, so that a deployment reads
 * one answer to *where is this published* rather than one vocabulary per half.
 */
enum class ScopePublicationPlace {
    /**
     * `scopes_supported` in the discovery document.
     */
    DISCOVERY
}
