package com.sympauthy.business.flow.model

import java.net.URI

/**
 * Where, and how, the end-user is handed back to whoever started an [InteractiveFlowSession] once it ends,
 * whether it completed, failed or was left.
 */
data class InteractiveFlowHandBack(
    /**
     * The URL the end-user is sent to: the client's `redirect_uri` for an
     * [InteractiveFlowRedirectType.AUTHORIZATION_CODE] session, the return URI the start request resolved to for a
     * [InteractiveFlowRedirectType.PLAIN] one.
     */
    val uri: URI,

    /**
     * How [uri] is decorated with the outcome of the session.
     */
    val type: InteractiveFlowRedirectType,

    /**
     * The value the start request carried as `return_state`, sent back unchanged as `sympauthy_return_state`.
     * Always null for an [InteractiveFlowRedirectType.AUTHORIZATION_CODE] session, which echoes the client's own
     * `state` from its OAuth2 record instead.
     */
    val returnState: String? = null,
)
