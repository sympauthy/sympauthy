package com.sympauthy.business.model.flow

import java.util.*

/**
 * The intent of an [InteractiveFlowPurpose.LINK_PROVIDER] purpose: which third-party identity provider is to
 * be linked to the session's already-fixed [InteractiveFlowSession.userId], attached to an
 * [InteractiveFlowSession].
 *
 * Keyed by [sessionId] and fetched via
 * [com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager], never carried on the
 * session itself. It holds only the *intent* (the target provider, set at session start); the transient
 * provider-authorization state (provider id + OIDC nonce) lives in [InteractiveFlowSessionProvider], and the
 * durable link, once created, in the provider user-info store.
 */
data class InteractiveFlowSessionLinkProvider(
    /**
     * Identifier of the [InteractiveFlowSession] this link intent is attached to.
     */
    val sessionId: UUID,

    /**
     * Identifier of the third-party identity provider to link to the session's user.
     */
    val providerId: String,
)
