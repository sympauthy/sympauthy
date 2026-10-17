package com.sympauthy.business.manager.auth

import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.GrantRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Deletes every expired token, and then every grant that is finished: revoked, or with no token left.
 *
 * A refresh adds an access token to its grant every time, so a grant refreshed for months would otherwise
 * keep every one of them. A revoked grant goes on the run after its revocation, with its tokens: nothing
 * reads who revoked a grant once it is written, and a token whose grant is gone is refused as surely as one
 * whose grant is revoked. A refresh token without an expiry keeps its grant for as long as it is not revoked.
 *
 * A grant is kept while a token or a stored authorization code names it, revoked or not: a code still to be
 * exchanged is about to be issued tokens under it, and one already exchanged revokes it if it is presented
 * again. The code is collected with its interactive flow session, so a grant whose code was never exchanged
 * goes once that session does.
 *
 * Nothing it deletes is something a request still in flight needs. A code exchange adds its tokens to the
 * grant its code names, and a refresh to a grant through a refresh token that has not expired, which keeps
 * either grant from being finished; a token being read under a revoked grant is refused whether its rows are
 * still there or not.
 */
@Singleton
open class GrantCleaner(
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val grantRepository: GrantRepository
) {

    @Transactional
    open suspend fun clean(): CleanResult {
        val expiredTokenCount = tokenRepository.deleteByExpirationDateLessThan(LocalDateTime.now())
        val revokedTokenCount = tokenRepository.deleteUnderRevokedGrants()
        val grantCount = grantRepository.deleteFinished()
        return CleanResult(
            tokenCount = expiredTokenCount + revokedTokenCount,
            grantCount = grantCount
        )
    }

    data class CleanResult(
        val tokenCount: Int,
        val grantCount: Int
    )
}
