package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.auth.GrantManager
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
     * still live: it still exists, has not been revoked, and still holds a token that has not expired.
     *
     * An actor grant that is gone, revoked or lapsed throws an `OAuth2Exception` carrying [INVALID_GRANT] and
     * the code naming which of the three it was: `grant.actor_invalid`, `grant.actor_revoked` or
     * `grant.actor_expired`.
     *
     * Does nothing when [grant] is not an act-as grant, which is any grant whose [Grant.actorGrantId] is null.
     */
    suspend fun validateActorGrant(grant: Grant) {
        val actorGrantId = grant.actorGrantId ?: return
        val actorGrant = grantManager.findByIdOrNull(actorGrantId)
        when {
            actorGrant == null -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.actor_invalid")
            actorGrant.revoked -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.actor_revoked")
            tokenRepository.countLiveByGrantId(actorGrantId, LocalDateTime.now()) == 0L ->
                throw oauth2ExceptionOf(INVALID_GRANT, "grant.actor_expired")
        }
    }
}
