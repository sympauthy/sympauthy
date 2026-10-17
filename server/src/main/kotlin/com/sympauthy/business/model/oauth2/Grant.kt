package com.sympauthy.business.model.oauth2

import com.sympauthy.business.model.MaybeExpirable
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.user.claim.RequestedClaims
import java.time.LocalDateTime
import java.util.*

/**
 * What a client was allowed, which every token issued for it stands under: opened by an authorization
 * before it hands out its code, a `client_credentials` request or a token exchange, and descended from by
 * every refresh of the tokens it issued. A token is live only while its grant is, so revoking a grant takes
 * every token under it out of service at once.
 *
 * See `docs/design/grants.md`.
 */
data class Grant(
    val id: UUID,
    /**
     * User ID this grant was opened for. Null for a `client_credentials` grant, which no person is behind.
     */
    val userId: UUID?,
    val clientId: String,
    /**
     * Identifier of the audience this grant was opened for. For an act-as grant it is the audience the
     * exchange named, which need not be the acting client's.
     */
    val audienceId: String,
    /**
     * The grant type this grant serves: [GrantType.AUTHORIZATION_CODE] for the one an authorization opens
     * before handing out its code, [GrantType.CLIENT_CREDENTIALS] or [GrantType.TOKEN_EXCHANGE]. A refresh
     * opens none.
     */
    val grantType: GrantType,
    /**
     * Grantable scopes granted through granting rules.
     */
    val grantedScopes: List<String>,
    /**
     * When the grantable scopes were granted.
     */
    val grantedAt: LocalDateTime?,
    /**
     * How the grantable scopes were granted (auto or rule).
     */
    val grantedBy: GrantedBy?,
    /**
     * Consentable scopes obtained through user consent.
     */
    val consentedScopes: List<String>,
    /**
     * When the consentable scopes were consented.
     */
    val consentedAt: LocalDateTime?,
    /**
     * How the consentable scopes were consented (auto or user).
     */
    val consentedBy: ConsentedBy?,
    /**
     * Client scopes for client_credentials flows.
     */
    val clientScopes: List<String>,
    /**
     * When the person this grant was opened for proved a credential of their account, in the flow the
     * authorization behind it came from. Stated as `auth_time` by every token under this grant.
     *
     * Null where no person's authentication is behind the grant: a `client_credentials` grant has no person
     * at all, and a token exchange asserts an identity nobody proved.
     *
     * Every refresh issues its tokens under this grant, which is what keeps it from moving: an authentication
     * a month old states a month-old `auth_time` on a token minted a minute ago.
     */
    val authenticationDate: LocalDateTime?,
    /**
     * The claims the `claims` request parameter of the authorization behind this grant named, per OpenID
     * channel. [RequestedClaims.NONE] where no such request is behind the grant — a `client_credentials`
     * grant, a token exchange — and where the request named nothing.
     *
     * It outlives the flow session that received the parameter, so the id token a refresh mints and the
     * `/userinfo` answered with an access token of this grant honour the same request the code exchange did.
     */
    val requestedClaims: RequestedClaims,
    /**
     * Identifier of the interactive flow session this grant came from, kept for diagnosis. Nothing reads it:
     * the session is collected long before most grants end.
     */
    val sessionId: UUID?,
    /**
     * Identifier of the grant of the token a token exchange (RFC 8693) was given — the acting client's own
     * `client_credentials` grant. Present on an act-as grant, null otherwise.
     */
    val actorGrantId: UUID?,
    val creationDate: LocalDateTime,
    /**
     * When this grant can no longer be refreshed, as `auth.token.grant-expiration` set it. Null where it has
     * no lifetime of its own. No token under it expires later.
     */
    override val expirationDate: LocalDateTime?,
    /** Date and time at which this grant was revoked, or null if still active. */
    val revokedAt: LocalDateTime?,
    /** Actor who revoked this grant, or null if still active. */
    val revokedBy: GrantRevokedBy?,
    /** Identifier of the user or admin who revoked this grant, or null if still active or not applicable. */
    val revokedById: UUID?
) : MaybeExpirable {
    /** Whether this grant has been revoked. */
    val revoked: Boolean get() = revokedAt != null

    /** All scopes combined (granted + consented + client) for JWT `scope` claim. */
    val allScopes: List<String> get() = grantedScopes + consentedScopes + clientScopes

    /**
     * The expiry of a token issued under this grant whose own lifetime ends at [tokenExpirationDate]: the
     * earlier of the two, since no token outlives the grant it was issued under. Null only where neither
     * ends.
     */
    fun getTokenExpirationDate(tokenExpirationDate: LocalDateTime?): LocalDateTime? {
        return listOfNotNull(tokenExpirationDate, expirationDate).minOrNull()
    }
}
