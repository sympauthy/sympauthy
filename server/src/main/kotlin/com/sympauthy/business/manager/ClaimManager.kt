package com.sympauthy.business.manager

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimOrigin
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.ClaimsConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Provides unrestricted access to all claim definitions configured on this authorization server.
 *
 * No scope-based filtering is applied here. When listing claims to present to the end-user during the
 * authorization flow, use [com.sympauthy.business.manager.user.ConsentAwareClaimManager] instead, which
 * filters claims on the end-user's consented scopes.
 */
@Singleton
class ClaimManager(
    @Inject private val uncheckedClaimsConfig: ClaimsConfig,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    private val cachedClaimsMap by lazy {
        uncheckedClaimsConfig.orThrow().claims
            .associateBy { it.id }
    }

    /**
     * Return the [Claim] identified by [id] or null.
     *
     * Backed by a map built once, so looking up many ids in a row costs no more than the lookups.
     */
    fun findByIdOrNull(id: String): Claim? {
        return cachedClaimsMap[id]
    }

    /**
     * Return all [Claim] enabled on this authorization server.
     */
    fun listEnabledClaims(): List<Claim> {
        return cachedClaimsMap.values.filter { it.enabled }
    }

    /**
     * Return all [Claim] configured on this authorization server, including disabled ones.
     */
    fun listAllClaims(): List<Claim> {
        return cachedClaimsMap.values.toList()
    }

    /**
     * List all [Claim] that we want to present to the end-user during the authentication flow.
     */
    fun listCollectableClaims(): List<Claim> {
        return listEnabledClaims().filter(Claim::userInputted)
    }

    /**
     * Return all [Claim] required to be provided by the end-user during its authorization flow.
     */
    fun listRequiredClaims(): List<Claim> {
        return listEnabledClaims().filter(Claim::required)
    }

    /**
     * Return all the OpenID claims enabled on this authorization server.
     */
    fun listEnabledOpenIdConnectClaims(): List<Claim> {
        return cachedClaimsMap.values.filter { it.origin == ClaimOrigin.OPENID_CONNECT && it.enabled }
    }

    /**
     * Return all [Claim] configured as identifier claims.
     */
    fun listIdentifierClaims(): List<Claim> {
        return uncheckedAuthConfig.orThrow()
            .identifierClaims
            .mapNotNull { findByIdOrNull(it) }
    }
}
