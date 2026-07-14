package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.data.repository.AuthenticationTokenRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Validates the actor token backing an act-as token issued via OAuth 2.0 Token Exchange (RFC 8693).
 *
 * An act-as token records the id of the token that was exchanged to issue it in
 * [AuthenticationToken.actorTokenId] (the acting client's own client-credentials token presented as the
 * `subject_token`). This validator re-checks that actor token whenever the act-as token is validated, so the
 * delegation cannot outlive the actor token it was derived from.
 */
@Singleton
class ActorTokenValidator(
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: AuthenticationTokenMapper
) {

    /**
     * For an act-as token (RFC 8693) — one carrying an [AuthenticationToken.actorTokenId] — verify the actor token
     * (the acting client's client-credentials token that was exchanged) is still valid: it still exists, has not
     * been revoked, and has not expired.
     *
     * No-op when [token] is not an act-as token ([AuthenticationToken.actorTokenId] is null).
     *
     * @throws OAuth2Exception ([INVALID_GRANT]) if the actor token is missing, revoked, or expired.
     */
    suspend fun validateActorToken(token: AuthenticationToken) {
        val actorTokenId = token.actorTokenId ?: return
        val actorToken = tokenRepository.findById(actorTokenId)?.let(tokenMapper::toToken)
        when {
            actorToken == null -> throw oauth2ExceptionOf(INVALID_GRANT, "token.actor_invalid")
            actorToken.revoked -> throw oauth2ExceptionOf(INVALID_GRANT, "token.actor_revoked")
            actorToken.expired -> throw oauth2ExceptionOf(INVALID_GRANT, "token.actor_expired")
        }
    }
}
