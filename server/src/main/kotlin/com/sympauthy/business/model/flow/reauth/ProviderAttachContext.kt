package com.sympauthy.business.model.flow.reauth

import com.sympauthy.business.model.reauth.ReAuthenticationMethod
import com.sympauthy.business.model.user.CollectedClaim

/**
 * Everything the sign-in page needs to render the re-authentication banner for an in-progress interactive provider
 * attach: which account is being confirmed, which provider is being attached, and which credentials the account can
 * re-authenticate with.
 */
data class ProviderAttachContext(
    /**
     * Identifier claims (e.g. email) of the existing account the end-user must prove ownership of.
     */
    val targetIdentifierClaims: List<CollectedClaim>,
    /**
     * Identifier of the provider being attached.
     */
    val providerId: String,
    /**
     * Display name of the provider being attached.
     */
    val providerName: String,
    /**
     * Credentials the target account can re-authenticate with.
     */
    val availableMethods: Set<ReAuthenticationMethod>
)
