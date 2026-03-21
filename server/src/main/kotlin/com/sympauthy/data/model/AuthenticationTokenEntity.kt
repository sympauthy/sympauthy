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
    val authorizeAttemptId: UUID?,
    /**
     * The OAuth2 grant type used to generate this token.
     * Examples: "authorization_code", "refresh_token", "client_credentials"
     */
    val grantType: String,

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
