package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("authentication_tokens")
class AuthenticationTokenEntity(
    val type: String,
    /**
     * User ID associated with this token.
     * This can be null for tokens issued via client credentials flow (machine-to-machine).
     */
    val userId: UUID?,
    val clientId: String,
    /**
     * Scopes granted through granting rules (grantable scopes only).
     */
    val grantedScopes: Array<String>,
    val grantedAt: LocalDateTime? = null,
    val grantedBy: String? = null,
    /**
     * Scopes obtained through user consent (consentable scopes only).
     */
    val consentedScopes: Array<String>,
    val consentedAt: LocalDateTime? = null,
    val consentedBy: String? = null,
    /**
     * Scopes for client_credentials flows (client scopes only).
     */
    val clientScopes: Array<String>,
    /**
     * There is no foreign key.
     * This can be null for tokens issued via client credentials flow.
     */
    val sessionId: UUID?,
    /**
     * The OAuth2 grant type used to generate this token.
     * Examples: "authorization_code", "refresh_token", "client_credentials"
     */
    val grantType: String,

    /**
     * When the person this token was issued for proved a credential of their account, in the flow the
     * authorization behind it came from.
     *
     * Null where no person's authentication is behind the token: a `client_credentials` grant has no
     * person at all, and a token exchange asserts an identity nobody proved. A refresh carries the
     * original authentication's date unchanged, which is what keeps it from looking a minute old a month
     * on.
     */
    val authenticationDate: LocalDateTime? = null,

    /**
     * JWK SHA-256 Thumbprint (RFC 7638) of the DPoP public key this token is bound to.
     * Null for bearer tokens (no DPoP binding).
     */
    val dpopJkt: String? = null,

    /**
     * Id of the token that was exchanged to issue this token via OAuth 2.0 Token Exchange (RFC 8693).
     * For an act-as token, this is the acting client's own client-credentials token presented as the `subject_token`.
     * Null for tokens not issued through token exchange.
     */
    val actorTokenId: UUID? = null,

    val revokedAt: LocalDateTime? = null,
    val revokedBy: String? = null,
    val revokedById: UUID? = null,
    val issueDate: LocalDateTime,
    val expirationDate: LocalDateTime?
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
