package com.sympauthy.business.model.token

import com.sympauthy.business.model.MaybeExpirable
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import java.time.LocalDateTime
import java.util.*

/**
 * A token this server issued, with the [grant] it was issued under: everything the token shares with the
 * other tokens of its grant is the grant's, and the token records only what is its own.
 */
data class AuthenticationToken(
    val id: UUID,
    val type: AuthenticationTokenType,
    val grant: Grant,
    /**
     * The grant type that issued this token, which is [GrantType.REFRESH_TOKEN] for every token a refresh
     * issued under the [grant] another grant type opened.
     */
    val grantType: GrantType,
    /**
     * JWK SHA-256 Thumbprint (RFC 7638) of the DPoP public key this token is bound to.
     * Null for bearer tokens (no DPoP binding).
     */
    val dpopJkt: String? = null,
    val issueDate: LocalDateTime,
    /**
     * When this token expires, never later than the [grant] does.
     */
    override val expirationDate: LocalDateTime?
) : MaybeExpirable
