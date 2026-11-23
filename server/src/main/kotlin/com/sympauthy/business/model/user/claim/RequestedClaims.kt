package com.sympauthy.business.model.user.claim

/**
 * The claims a client named in the `claims` request parameter of OpenID Connect Core §5.5, per OpenID
 * channel it named them in. It only adds, where [EnabledClaim.publishedInWhenRequested] opened a
 * channel, and is asked after every permission test; see `docs/design/claims.md`.
 *
 * **Both sets hold claim ids and are sanitized.** A name matching no claim this server serves is dropped
 * where the parameter is parsed, so nothing downstream re-reads a document or carries a name that can
 * fail to resolve. [com.sympauthy.business.manager.authorization.RequestedClaimsManager.parseRequestedClaims]
 * is that parse.
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
         * parameter and where it sent one naming no claim this server serves.
         */
        val NONE = RequestedClaims(idTokenClaimIds = emptySet(), userInfoClaimIds = emptySet())

        /**
         * The request a row holds, read from the column carrying each channel's names.
         *
         * Every row that carries a request carries it as these two columns, and pairing a column with
         * the channel it belongs to is the one thing a reader of one can get wrong — so it is done here
         * rather than at each mapper. A writer cannot share this: the two entity constructors name the
         * columns themselves, and the `data` layer may not see this model. What holds every writer to
         * the pairing instead is `RequestedClaimPublicationFeatureIT`, whose two claims open both
         * channels and are asked for in one each, so a channel swapped anywhere between the authorize
         * request and `/userinfo` fails it.
         */
        fun of(idTokenClaimIds: Array<String>, userInfoClaimIds: Array<String>) = RequestedClaims(
            idTokenClaimIds = idTokenClaimIds.toSet(),
            userInfoClaimIds = userInfoClaimIds.toSet()
        )
    }
}
