package com.sympauthy.business.model.user.claim

/**
 * The claims a client named in the `claims` request parameter of OpenID Connect Core §5.5, per OpenID
 * channel it named them in.
 *
 * **A request adds, and it adds only where a deployment opened the channel to one.** §5.5 is additive in
 * as many words — the claims listed "are being requested to be added to any Claims that are being
 * requested using scope values" — so this is never a way to ask for less, and what it may add is what
 * [Claim.publishedInWhenRequested] names. A claim a request names and no file opened is carried nowhere
 * it was not already.
 *
 * **It is asked last, and it narrows nothing the permission tests settled.** The ACL decides whether the
 * caller may know the value at all, the audience whether it is this audience's to know, and
 * [Claim.publishedIn] which channel carries it; only then does this open an on-request channel. A claim
 * the person never consented to, or one restricted to another audience, is absent however a request names
 * it — see `docs/design/claims.md`.
 *
 * **Both sets hold claim ids and are sanitized.** A name matching no configured claim is dropped where
 * the parameter is parsed, so nothing downstream re-reads a document or carries a name that can fail to
 * resolve. [com.sympauthy.business.manager.ClaimManager.parseRequestedClaims] is that parse.
 */
data class RequestedClaims(
    /**
     * The ids the `id_token` member named.
     */
    val idTokenClaimIds: Set<String>,
    /**
     * The ids the `userinfo` member named.
     */
    val userInfoClaimIds: Set<String>
) {

    /**
     * The claim ids this request named in [place], which is empty for a place §5.5 defines no member for —
     * the ones [ClaimPublicationPlace.nameableInAClaimsRequest] is false of. A caller filtering on any
     * place may therefore ask this without knowing which of them a request can reach.
     */
    fun namesIn(place: ClaimPublicationPlace): Set<String> = when (place) {
        ClaimPublicationPlace.ID_TOKEN -> idTokenClaimIds
        ClaimPublicationPlace.USERINFO -> userInfoClaimIds
        ClaimPublicationPlace.ACCESS_TOKEN,
        ClaimPublicationPlace.INTROSPECTION,
        ClaimPublicationPlace.DISCOVERY -> emptySet()
    }

    companion object {
        /**
         * The request that named nothing, which is what a grant carries where the client sent no `claims`
         * parameter and where it sent one naming no claim this deployment configures.
         */
        val NONE = RequestedClaims(idTokenClaimIds = emptySet(), userInfoClaimIds = emptySet())
    }
}
