package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.data.model.GrantEntity
import com.sympauthy.monitoring.telemetry.SpanReference
import com.sympauthy.util.enumValueOfWireNameOrNull
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class GrantMapper {

    fun toGrant(entity: GrantEntity): Grant {
        val grant = Grant(
            id = entity.id ?: throw invalidProperty("id"),
            userId = entity.userId,
            clientId = entity.clientId,
            audienceId = entity.audienceId,
            grantType = mapGrantType(entity.grantType),
            grantedScopes = entity.grantedScopes.toList(),
            grantedAt = entity.grantedAt,
            grantedBy = mapGrantedBy(entity.grantedBy),
            consentedScopes = entity.consentedScopes.toList(),
            consentedAt = entity.consentedAt,
            consentedBy = mapConsentedBy(entity.consentedBy),
            clientScopes = entity.clientScopes.toList(),
            authenticationDate = entity.authenticationDate,
            requestedClaims = mapRequestedClaims(entity),
            sessionId = entity.sessionId,
            sessionStartedBy = SpanReference.ofOrNull(
                entity.sessionStartedByTraceId,
                entity.sessionStartedBySpanId
            ),
            actorGrantId = entity.actorGrantId,
            creationDate = entity.creationDate,
            expirationDate = entity.expirationDate,
            revokedAt = entity.revokedAt,
            revokedBy = mapRevokedBy(entity.revokedBy),
            revokedById = entity.revokedById
        )
        validateGrantCoherence(grant)
        return grant
    }

    /**
     * The grant type [value] names, refusing the row where it names none that opens a grant: a refresh
     * issues its tokens under the grant of the refresh token it was given, and opens none of its own.
     */
    fun mapGrantType(value: String): GrantType {
        return when (val grantType = enumValueOfWireNameOrNull<GrantType>(value)) {
            null, GrantType.REFRESH_TOKEN -> throw invalidProperty("grantType")
            else -> grantType
        }
    }

    /**
     * The claims the request behind the grant named, read out of the one column per OpenID channel the row
     * carries them in.
     *
     * No row is refused here: a column holds the claim ids the authorize endpoint had already sanitized, so
     * a name whose claim the configuration no longer declares reads back as itself and stops mattering where
     * it is compared against the claims a caller may read.
     */
    fun mapRequestedClaims(entity: GrantEntity): RequestedClaims = RequestedClaims.of(
        idTokenClaimIds = entity.requestedIdTokenClaims,
        userInfoClaimIds = entity.requestedUserinfoClaims
    )

    fun mapConsentedBy(value: String?): ConsentedBy? = value?.let {
        try {
            ConsentedBy.valueOf(it)
        } catch (e: IllegalArgumentException) {
            throw invalidProperty("consentedBy", e)
        }
    }

    fun mapGrantedBy(value: String?): GrantedBy? = value?.let {
        try {
            GrantedBy.valueOf(it)
        } catch (e: IllegalArgumentException) {
            throw invalidProperty("grantedBy", e)
        }
    }

    /**
     * Who revoked the grant [value] names, or null where it is not revoked.
     */
    fun mapRevokedBy(value: String?): GrantRevokedBy? = value?.let {
        try {
            GrantRevokedBy.valueOf(it)
        } catch (e: IllegalArgumentException) {
            throw invalidProperty("revokedBy", e)
        }
    }

    /**
     * The failure thrown when the column [invalidProperty] is missing or holds a value no enum of the model
     * names.
     *
     * It is separate from the coherence codes below, which name a row whose columns each read back but
     * contradict each other; this one names a column that does not read back at all.
     */
    private fun invalidProperty(invalidProperty: String, cause: Throwable? = null): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.grant.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }

    private fun validateGrantCoherence(grant: Grant) {
        when (grant.grantType) {
            GrantType.CLIENT_CREDENTIALS -> {
                if (grant.userId != null) {
                    throw incoherence("mapper.grant.client_credentials.invalid_user_id", grant)
                }
                if (grant.sessionId != null) {
                    throw incoherence("mapper.grant.client_credentials.invalid_session_id", grant)
                }
                requireNoAuthenticationDate(grant)
                requireNoActorGrantId(grant)
            }

            GrantType.TOKEN_EXCHANGE -> {
                // An act-as grant names a target user but is not tied to an authorization flow.
                if (grant.userId == null) {
                    throw incoherence("mapper.grant.missing_user_id", grant)
                }
                if (grant.sessionId != null) {
                    throw incoherence("mapper.grant.token_exchange.invalid_session_id", grant)
                }
                requireNoAuthenticationDate(grant)
                // An act-as grant is always opened by exchanging a token of another grant.
                if (grant.actorGrantId == null) {
                    throw incoherence("mapper.grant.token_exchange.missing_actor_grant_id", grant)
                }
            }

            else -> {
                if (grant.userId == null) {
                    throw incoherence("mapper.grant.missing_user_id", grant)
                }
                if (grant.sessionId == null) {
                    throw incoherence("mapper.grant.missing_session_id", grant)
                }
                requireNoActorGrantId(grant)
            }
        }
    }

    /**
     * A grant no person's authentication is behind must carry no [Grant.authenticationDate].
     *
     * A client-credentials grant has no person at all and a token exchange asserts an identity nobody
     * proved, so a date on either would be published as an `auth_time` naming an authentication that never
     * happened — and a resource server deciding recency off it would let through exactly the unattended
     * caller it was asking about.
     */
    private fun requireNoAuthenticationDate(grant: Grant) {
        if (grant.authenticationDate != null) {
            throw incoherence("mapper.grant.invalid_authentication_date", grant)
        }
    }

    /**
     * The [Grant.actorGrantId] must only be set on a grant a token exchange opened.
     */
    private fun requireNoActorGrantId(grant: Grant) {
        if (grant.actorGrantId != null) {
            throw incoherence("mapper.grant.invalid_actor_grant_id", grant)
        }
    }

    private fun incoherence(detailsId: String, grant: Grant): BusinessException {
        return internalBusinessExceptionOf(detailsId, "grantType" to grant.grantType.wireName)
    }
}
