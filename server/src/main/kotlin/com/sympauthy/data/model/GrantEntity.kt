package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("grants")
class GrantEntity(
    /**
     * User ID this grant was opened for. Null for a grant opened by a `client_credentials` request, which
     * no person is behind.
     */
    val userId: UUID?,
    val clientId: String,
    /**
     * Identifier of the audience this grant was opened for, whose token audience is the `aud` its tokens
     * state. For an act-as grant it is the audience the exchange named, which need not be the acting
     * client's.
     *
     * Recorded rather than read off the client because a client's audience is configuration, and moves or
     * disappears with it: revoking a consent reaches the grants opened for that audience, whatever has become
     * of the clients they were opened for since.
     */
    val audienceId: String,
    /**
     * The OAuth2 grant type this grant serves, as it is published: `authorization_code` for the grant an
     * authorization opens before handing out its code, `client_credentials` or the token exchange URN. A
     * refresh opens none.
     */
    val grantType: String,
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
     * When the person this grant was opened for proved a credential of their account, in the flow the
     * authorization behind it came from. Null where no person's authentication is behind the grant.
     */
    val authenticationDate: LocalDateTime? = null,
    /**
     * The claim ids the `claims` request parameter of the authorization behind this grant named for the id
     * token, and the ones it named for `/userinfo`. Empty on a grant no such request is behind, and where
     * the request named nothing.
     */
    val requestedIdTokenClaims: Array<String> = emptyArray(),
    val requestedUserinfoClaims: Array<String> = emptyArray(),
    /**
     * Identifier of the interactive flow session this grant came from, which the code it handed back is found by
     * and a token request under the grant is traced with. There is no foreign key: the session is collected long
     * before most grants end, so the reference is left dangling by design.
     *
     * A session opens at most one grant, which a unique index over the column holds; a grant no session
     * opened holds null, which the index admits any number of.
     */
    val sessionId: UUID? = null,
    /**
     * The trace and span id of the request that started the session in [sessionId], both set or both null: a
     * copy of the session's, kept for every request redeeming or refreshing the grant to link to once the session
     * is collected.
     */
    val sessionStartedByTraceId: String? = null,
    val sessionStartedBySpanId: String? = null,
    /**
     * Identifier of the grant of the token a token exchange (RFC 8693) was given — the acting client's own
     * `client_credentials` grant. Null on a grant no exchange opened.
     *
     * There is no foreign key: that grant is collected once its token expires, and an act-as grant naming
     * one that is gone is refused rather than kept from being collected.
     */
    val actorGrantId: UUID? = null,
    val creationDate: LocalDateTime,
    /**
     * When this grant can no longer be refreshed, as `auth.grant.expiration` set it when the grant was
     * opened. Null where it has no lifetime of its own.
     */
    val expirationDate: LocalDateTime? = null,
    val revokedAt: LocalDateTime? = null,
    val revokedBy: String? = null,
    val revokedById: UUID? = null
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
