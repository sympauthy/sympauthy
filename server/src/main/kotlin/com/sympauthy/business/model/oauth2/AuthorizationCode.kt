package com.sympauthy.business.model.oauth2

import com.sympauthy.business.model.Expirable
import java.time.LocalDateTime
import java.util.*

/**
 * The code an authorization hands out to the client, carrying what its exchange is checked against that the
 * grant it names does not hold. An exchange reads it and its grant, never the interactive flow session that
 * issued it — see `docs/design/oauth2-authorization.md`.
 */
data class AuthorizationCode(
    /**
     * Identifier of the grant the authorization opened before handing out this code, which holds no other.
     */
    val grantId: UUID,
    val code: String,
    /**
     * The PKCE challenge of the authorization request (RFC 7636), which the `code_verifier` of the exchange
     * has to answer.
     */
    val codeChallenge: String,
    val codeChallengeMethod: CodeChallengeMethod,
    /**
     * The `nonce` of the authorization request, repeated by the id token the exchange issues. Null where the
     * request carried none.
     */
    val nonce: String?,
    val creationDate: LocalDateTime,
    override val expirationDate: LocalDateTime
) : Expirable
