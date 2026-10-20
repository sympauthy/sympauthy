package com.sympauthy.business.manager.auth

import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.GrantRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime

/**
 * Deletes every expired token and authorization code, and then every grant that is finished: revoked, or with
 * no token left.
 *
 * A refresh adds an access token to its grant every time, so a grant refreshed for months would otherwise
 * keep every one of them. A revoked grant goes on the run after its revocation, with its tokens: nothing
 * reads who revoked a grant once it is written, and a token whose grant is gone is refused as surely as one
 * whose grant is revoked. A refresh token without an expiry keeps its grant for as long as it is not revoked.
 *
 * A grant is kept while a token or a stored authorization code names it, revoked or not: a code still to be
 * exchanged is about to be issued tokens under it, and one already exchanged revokes it if it is presented
 * again. A code is collected once its own lifetime has ended, exchanged or not, so a grant whose code was
 * never exchanged goes once the code does.
 *
 * Nothing it deletes is something a request still in flight needs. A code exchange adds its tokens to the
 * grant its code names, and a refresh to a grant through a refresh token it found unexpired, which keeps
 * either grant from being finished; a token being read under a revoked grant is refused whether its rows are
 * still there or not. A token or a code is deleted only once [EXPIRATION_GRACE] has passed since it expired,
 * so a refresh that found its token unexpired a moment before, or an exchange its code, still finds the grant
 * to write under.
 */
@Singleton
open class GrantCleaner(
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val authorizationCodeRepository: AuthorizationCodeRepository,
    @Inject private val grantRepository: GrantRepository
) {

    @Transactional
    open suspend fun clean(): CleanResult {
        val cutoff = LocalDateTime.now().minus(EXPIRATION_GRACE)
        val expiredTokenCount = tokenRepository.deleteByExpirationDateLessThan(cutoff)
        val revokedTokenCount = tokenRepository.deleteUnderRevokedGrants()
        val authorizationCodeCount = authorizationCodeRepository.deleteByExpirationDateLessThan(cutoff)
        val grantCount = grantRepository.deleteFinished()
        return CleanResult(
            tokenCount = expiredTokenCount + revokedTokenCount,
            authorizationCodeCount = authorizationCodeCount,
            grantCount = grantCount
        )
    }

    data class CleanResult(
        val tokenCount: Int,
        val authorizationCodeCount: Int,
        val grantCount: Int
    )

    companion object {
        /**
         * How long an expired token or code is kept before it is deleted: far longer than a request takes, and
         * short enough beside the job's own schedule that nothing waits on it.
         */
        val EXPIRATION_GRACE: Duration = Duration.ofMinutes(5)
    }
}
