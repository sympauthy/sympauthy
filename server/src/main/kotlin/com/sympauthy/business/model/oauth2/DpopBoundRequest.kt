package com.sympauthy.business.model.oauth2

import java.net.URI

/**
 * The values of an incoming HTTP request that a DPoP proof is bound to (RFC 9449). Carrying them as a value
 * object is what keeps the proof validation callable without an HTTP request.
 */
data class DpopBoundRequest(
    /**
     * The HTTP method, which the proof must repeat in its `htm` claim.
     */
    val method: String,
    /**
     * The uri the request was addressed to. Only its path is read: RFC 9449 requires the `htu` claim to carry
     * neither query nor fragment, and the path is resolved against the server's own configured root, so a
     * proof minted for another host is refused whatever the request says it was sent to.
     */
    val uri: URI,
    /**
     * The access token the request presents beside the proof, whose hash the proof must carry in its `ath`
     * claim, or null where the request presents none — a token request, which is asking for one.
     */
    val accessToken: String?
)
