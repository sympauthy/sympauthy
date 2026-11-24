package com.sympauthy.business.user.model

import com.sympauthy.business.claim.model.CollectedClaim
import com.sympauthy.business.consent.model.Consent
import com.sympauthy.business.provider.model.ProviderUserInfo

/**
 * Aggregated user information visible to a client through the Client API.
 *
 * Combines user identity, identifier claims, linked provider accounts,
 * and the active consent granted to the requesting client.
 */
data class ClientUser(
    val user: User,
    val identifierClaims: List<CollectedClaim>,
    val providers: List<ProviderUserInfo>,
    val consent: Consent
)
