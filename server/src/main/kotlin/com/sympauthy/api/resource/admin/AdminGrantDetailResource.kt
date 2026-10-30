package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

internal const val GRANT_ID_PROPERTY_DESCRIPTION = "Identifier of the grant."

internal const val GRANT_USER_ID_PROPERTY_DESCRIPTION =
        "Identifier of the user the grant was opened for. On a grant a client obtained through a token exchange, " +
        "the user it acts as. Absent on a `client_credentials` grant, which no user holds."

internal const val GRANT_CLIENT_ID_PROPERTY_DESCRIPTION =
        "Identifier of the client the grant was opened for, which the configuration may no longer declare."

internal const val GRANT_AUDIENCE_ID_PROPERTY_DESCRIPTION =
        "Identifier of the audience the grant was opened for, which the configuration may no longer declare."

internal const val GRANT_TYPE_PROPERTY_DESCRIPTION =
        "Grant type the grant was opened by. A refresh opens none: its tokens are issued under the grant of the " +
        "refresh token it was given."

internal const val GRANT_STATUS_PROPERTY_DESCRIPTION =
        "Where the grant stands. `live`: not revoked, not past its lifetime, and holding a token that has not " +
        "expired or a code still to be exchanged. `revoked`: revoked, whatever else holds of it. `expired`: past " +
        "its lifetime. `lapsed`: neither revoked nor past its lifetime, but left with no token that has not " +
        "expired and no code still to be exchanged."

internal const val GRANT_CREATED_AT_PROPERTY_DESCRIPTION = "Date and time at which the grant was opened, in UTC."

internal const val GRANT_EXPIRES_AT_PROPERTY_DESCRIPTION =
        "Date and time past which the grant can no longer be refreshed, in UTC. Absent when the grant has no " +
        "lifetime of its own."

internal const val GRANT_REVOKED_AT_PROPERTY_DESCRIPTION =
        "Date and time at which the grant was revoked, in UTC. Absent unless it is `revoked`."

internal const val GRANT_REVOKED_BY_PROPERTY_DESCRIPTION =
        "What revoked the grant: `client` is its client, through the revocation endpoint; `admin` an " +
        "administrator; `consent_revoked` the consent it rests on being revoked; `authorization_code_replayed` " +
        "its authorization code being presented again. Absent unless it is `revoked`."

internal const val GRANT_ACTOR_GRANT_ID_PROPERTY_DESCRIPTION =
        "Identifier of the grant of the token a token exchange was given, which revoking revokes this grant too. " +
        "Absent unless a client obtained the grant through a token exchange."

private const val NO_AUTHORIZATION_BEHIND = "Absent when no authorization request is behind the grant: on a " +
        "`client_credentials` grant and on a token exchange."

@Schema(
    description = "A grant, with everything it records: what a client was allowed, under which every token is " +
            "issued."
)
@Serdeable
data class AdminGrantDetailResource(
    @get:Schema(description = GRANT_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("grant_id")
    val grantId: UUID,
    @get:Schema(description = GRANT_USER_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("user_id")
    val userId: UUID?,
    @get:Schema(description = GRANT_CLIENT_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("client_id")
    val clientId: String,
    @get:Schema(description = GRANT_AUDIENCE_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(
        description = GRANT_TYPE_PROPERTY_DESCRIPTION,
        allowableValues = [
            "authorization_code",
            "client_credentials",
            "urn:ietf:params:oauth:grant-type:token-exchange"
        ]
    )
    @get:JsonProperty("grant_type")
    val grantType: String,
    @get:Schema(
        description = GRANT_STATUS_PROPERTY_DESCRIPTION,
        allowableValues = ["live", "revoked", "expired", "lapsed"]
    )
    @get:JsonProperty("status")
    val status: String,
    @get:Schema(description = GRANT_CREATED_AT_PROPERTY_DESCRIPTION)
    @get:JsonProperty("created_at")
    val createdAt: LocalDateTime,
    @get:Schema(description = GRANT_EXPIRES_AT_PROPERTY_DESCRIPTION)
    @get:JsonProperty("expires_at")
    val expiresAt: LocalDateTime?,
    @get:Schema(description = GRANT_REVOKED_AT_PROPERTY_DESCRIPTION)
    @get:JsonProperty("revoked_at")
    val revokedAt: LocalDateTime?,
    @get:Schema(
        description = GRANT_REVOKED_BY_PROPERTY_DESCRIPTION,
        allowableValues = ["client", "admin", "consent_revoked", "authorization_code_replayed"]
    )
    @get:JsonProperty("revoked_by")
    val revokedBy: String?,
    @get:Schema(
        description = "Identifier of the user who revoked the grant as an administrator. Absent unless " +
                "`revoked_by` is `admin`."
    )
    @get:JsonProperty("revoked_by_user_id")
    val revokedByUserId: UUID?,
    @get:Schema(description = GRANT_ACTOR_GRANT_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("actor_grant_id")
    val actorGrantId: UUID?,
    @get:Schema(description = "Scopes granted to the end-user by the scope granting rules.")
    @get:JsonProperty("granted_scopes")
    val grantedScopes: List<String>,
    @get:Schema(
        description = "Date and time at which the scopes in `granted_scopes` were granted, in UTC. " +
                NO_AUTHORIZATION_BEHIND
    )
    @get:JsonProperty("granted_at")
    val grantedAt: LocalDateTime?,
    @get:Schema(
        description = "How the scopes in `granted_scopes` were granted: `auto` without any rule, `rule` by a " +
                "scope granting rule, `webhook` by the authorization webhook of the client. " +
                NO_AUTHORIZATION_BEHIND,
        allowableValues = ["auto", "rule", "webhook"]
    )
    @get:JsonProperty("granted_by")
    val grantedBy: String?,
    @get:Schema(description = "Scopes the end-user consented to.")
    @get:JsonProperty("consented_scopes")
    val consentedScopes: List<String>,
    @get:Schema(
        description = "Date and time at which the scopes in `consented_scopes` were consented to, in UTC. " +
                NO_AUTHORIZATION_BEHIND
    )
    @get:JsonProperty("consented_at")
    val consentedAt: LocalDateTime?,
    @get:Schema(
        description = "How the scopes in `consented_scopes` were consented to: `auto` without the end-user " +
                "being asked, `user` by the end-user on the consent step. " + NO_AUTHORIZATION_BEHIND,
        allowableValues = ["auto", "user"]
    )
    @get:JsonProperty("consented_by")
    val consentedBy: String?,
    @get:Schema(description = "Scopes given to the client on its own behalf, by the `client_credentials` grant.")
    @get:JsonProperty("client_scopes")
    val clientScopes: List<String>,
    @get:Schema(
        description = "Date and time at which the end-user authenticated in the flow the grant was opened from, " +
                "in UTC. It is the `auth_time` every token under the grant states. Absent when no end-user " +
                "authenticated: on a `client_credentials` grant and on a token exchange."
    )
    @get:JsonProperty("authenticated_at")
    val authenticatedAt: LocalDateTime?,
    @get:Schema(
        description = "Claims the `claims` parameter of the authorization request asked for. Absent when the " +
                "request asked for none."
    )
    @get:JsonProperty("requested_claims")
    val requestedClaims: AdminGrantRequestedClaimsResource?
)

@Schema(
    description = "Claims the `claims` parameter of an authorization request asked for, per member of that " +
            "parameter."
)
@Serdeable
data class AdminGrantRequestedClaimsResource(
    @get:Schema(description = "Identifiers of the claims asked for in the id token.")
    @get:JsonProperty("id_token")
    val idToken: List<String>,
    @get:Schema(description = "Identifiers of the claims asked for at the userinfo endpoint.")
    @get:JsonProperty("userinfo")
    val userinfo: List<String>
)
