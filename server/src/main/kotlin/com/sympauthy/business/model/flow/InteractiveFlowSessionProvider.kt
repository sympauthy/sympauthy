package com.sympauthy.business.model.flow

import java.util.*

/**
 * The state of authorizing the user through a third-party provider, attached to an
 * [InteractiveFlowSession].
 *
 * Present only while the user is being authenticated through an external OAuth2 / OpenID Connect
 * provider. Keyed by [sessionId] and fetched via
 * [com.sympauthy.business.manager.flow.InteractiveFlowSessionProviderManager], never carried on the
 * session itself. Upserted when the user picks (or switches) the provider they authenticate with.
 */
data class InteractiveFlowSessionProvider(
    /**
     * Identifier of the [InteractiveFlowSession] this provider authorization state is attached to.
     */
    val sessionId: UUID,

    /**
     * The identifier of the third-party provider the user is authenticating with.
     */
    val providerId: String,

    /**
     * The random part (jti) of the nonce JWT sent to the OIDC provider in the authorization request.
     * Only the jti is stored; the full nonce JWT can be reconstructed using
     * [com.sympauthy.business.manager.flow.InteractiveFlowSessionProviderManager.buildProviderNonceOrNull].
     */
    val providerNonceJsonWebTokenId: UUID? = null,
)
