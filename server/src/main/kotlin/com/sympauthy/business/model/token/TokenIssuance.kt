package com.sympauthy.business.model.token

/**
 * The [tokens] issued in answer to [request].
 */
data class TokenIssuance(
    val request: TokenRequest,
    val tokens: IssuedTokens
)
