package com.sympauthy.business.flow.provider.manager

import com.sympauthy.business.flow.manager.InteractiveFlowPurposeHandler
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.provider.model.EnabledProvider
import com.sympauthy.business.provider.model.RawProviderClaims
import java.util.UUID

/**
 * Establishes (signs in or signs up) the end-user of an interactive flow from a third-party provider
 * round-trip whose subject is not yet linked to any account.
 *
 * Creating or associating a user — enforcing sign-up rules, honoring `auth.user-merging-enabled` and the
 * configured identifier claims, and consuming any invitation — is the OAuth2-authorize consumer's concern.
 * The generic [InteractiveFlowSessionOAuth2ProviderManager] owns the provider protocol (authorize, callback,
 * token exchange, claim resolution) and delegates this establish outcome here, so it stays free of
 * consumer-specific identity logic. This is the same inversion as [InteractiveFlowPurposeHandler]: the
 * generic layer owns the abstraction, the consumer (`business.flow.auth.manager`) implements it.
 */
interface ProviderUserEstablisher {

    /**
     * Establish the end-user of the [session] from the [rawUserInfo] just resolved from [provider], whose
     * subject is not yet linked to any account. Returns the resulting user id and whether a new account was
     * created (sign-up) versus an existing account associated (sign-in).
     */
    suspend fun establishNewProviderUser(
        session: OnGoingInteractiveFlowSession,
        provider: EnabledProvider,
        rawUserInfo: RawProviderClaims
    ): ProviderUserEstablishment
}

/**
 * Outcome of [ProviderUserEstablisher.establishNewProviderUser]: the established [userId] and whether the
 * account was created ([signedUp] `true`) rather than an existing one associated.
 */
data class ProviderUserEstablishment(
    val userId: UUID,
    val signedUp: Boolean
)
