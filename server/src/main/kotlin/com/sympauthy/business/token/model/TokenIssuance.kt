package com.sympauthy.business.token.model

/**
 * The [tokens] issued in answer to [request].
 */
data class TokenIssuance(
    val request: TokenRequest,
    val tokens: IssuedTokens
)
