package com.sympauthy.business.manager.auth

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.model.oauth2.Grant
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.data.repository.AuthenticationTokenRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Validates the grant behind an act-as grant opened via OAuth 2.0 Token Exchange (RFC 8693).
 *
 * An act-as grant records the grant of the token that was exchanged to open it in [Grant.actorGrantId] — the
 * acting client's own client-credentials grant. This validator re-checks that grant whenever a token of the
 * act-as grant is validated, so the delegation cannot outlive the grant it was derived from.
 */
@Singleton
class ActorGrantValidator(
    @Inject private val grantManager: GrantManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository
) {

    /**
     * For an act-as grant — one carrying a [Grant.actorGrantId] — verify the grant of the acting client is
     * still live: it has not been revoked, is not past its own lifetime, and still holds a token that has not
     * expired.
     *
     * Throws an `OAuth2Exception` carrying [INVALID_GRANT] and `grant.actor_revoked` where the actor grant was
     * revoked, and `grant.actor_expired` where it expired — past its lifetime, left with no token that has not
     * expired, or gone.
     *
     * Does nothing when [grant] is not an act-as grant, which is any grant whose [Grant.actorGrantId] is null.
     */
    suspend fun validateActorGrant(grant: Grant) {
        val actorGrantId = grant.actorGrantId ?: return
        val actorGrant = grantManager.findByIdOrNull(actorGrantId)
        when {
            // Answered as expired because expiring is how an actor grant comes to be collected while an act-as
            // grant still names it: revoking one revokes the act-as grants opened from it too, so their tokens
            // are refused as revoked before their actor grant is looked at.
            actorGrant == null -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.actor_expired")
            actorGrant.revoked -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.actor_revoked")
            actorGrant.expired -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.actor_expired")
            tokenRepository.countLiveByGrantId(actorGrantId, LocalDateTime.now()) == 0L ->
                throw oauth2ExceptionOf(INVALID_GRANT, "grant.actor_expired")
        }
    }
}
