package com.sympauthy.business.model.token


/**
 * A request to the token endpoint, read into the grant it asks for, with every parameter that grant
 * requires present. What a request may carry for a grant and still be refused is a question about the
 * request alone, and the endpoint has answered it before one of these is built.
 */
sealed class TokenRequest {

    /**
     * Exchange of [code] for the tokens of the end-user it was issued for, OAuth 2.1 §4.1.3.
     */
    data class AuthorizationCode(
        val code: String,
        /**
         * The PKCE verifier the challenge [code] carries has to be derived from. Null where the request
         * carried none, which the exchange refuses.
         */
        val codeVerifier: String?
    ) : TokenRequest()

    /**
     * Issue of new tokens under the grant [refreshToken] was issued under, OAuth 2.1 §4.3.
     */
    data class RefreshToken(
        val refreshToken: String
    ) : TokenRequest()

    /**
     * Issue of an access token to the client itself, OAuth 2.1 §4.2.
     */
    data class ClientCredentials(
        /**
         * The client scopes asked for, separated by spaces. Null where the request asked for none.
         */
        val scope: String?
    ) : TokenRequest()

    /**
     * Exchange of the client's own [subjectToken] for an access token letting it act as
     * [requestedSubject], RFC 8693 §2.1. [subjectToken] is an access token, and so is the token asked for.
     */
    data class TokenExchange(
        val subjectToken: String,
        val requestedSubject: String,
        /**
         * The `audience` the token is asked for. Null where the request named none.
         */
        val audience: String?
    ) : TokenRequest() {

        companion object {
            /**
             * RFC 8693 token type identifier for an OAuth 2.0 access token, the one type a token exchange
             * takes and issues.
             */
            const val ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token"
        }
    }
}
