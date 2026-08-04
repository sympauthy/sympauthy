package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.user.RawProviderClaims
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
 * generic layer owns the abstraction, the consumer (`business.manager.flow.auth`) implements it.
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
