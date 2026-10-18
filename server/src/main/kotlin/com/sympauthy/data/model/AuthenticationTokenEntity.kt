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
    val grantId: UUID,
    /**
     * The OAuth2 grant type that issued this token, as it is published: `authorization_code`,
     * `refresh_token`, `client_credentials` or the token exchange URN.
     */
    val grantType: String,
    /**
     * JWK SHA-256 Thumbprint (RFC 7638) of the DPoP public key this token is bound to.
     * Null for bearer tokens (no DPoP binding).
     */
    val dpopJkt: String? = null,
    val issueDate: LocalDateTime,
    /**
     * When this token expires, never later than the grant it was issued under. For a refresh token that is
     * earlier than the expiry its JWT states wherever the grant ends first.
     */
    val expirationDate: LocalDateTime?
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
