package com.sympauthy.business.model.user

import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.provider.ProviderUserInfo

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
