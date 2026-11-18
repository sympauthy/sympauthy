package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.lock.LeasedJob.CLEAN_FINISHED_GRANTS
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.GrantRepository
import com.sympauthy.monitoring.telemetry.JobMetrics
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime

/**
 * Deletes every token and authorization code expired more than [EXPIRATION_GRACE] ago and every token of a
 * revoked grant, and then every grant left with neither a token nor a code. What is collected, and why
 * nothing it deletes is something a request still in flight needs, is `docs/design/grants.md`.
 */
@Singleton
class GrantCleaner(
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val authorizationCodeRepository: AuthorizationCodeRepository,
    @Inject private val grantRepository: GrantRepository,
    @Inject private val jobMetrics: JobMetrics
) {

    /**
     * Delete what is finished, and record each kind of row deleted under [CLEAN_FINISHED_GRANTS].
     *
     * Each delete commits on its own, so each is recorded as soon as it returns: a failure in a later one
     * leaves the rows an earlier one deleted gone, and counted.
     */
    suspend fun clean(): CleanResult {
        val cutoff = LocalDateTime.now().minus(EXPIRATION_GRACE)
        val job = CLEAN_FINISHED_GRANTS.leaseName
        val expiredTokenCount = tokenRepository.deleteByExpirationDateLessThan(cutoff)
        jobMetrics.recordDeletions(job, "token", expiredTokenCount.toLong())
        val revokedTokenCount = tokenRepository.deleteUnderRevokedGrants()
        jobMetrics.recordDeletions(job, "token", revokedTokenCount.toLong())
        val authorizationCodeCount = authorizationCodeRepository.deleteByExpirationDateLessThan(cutoff)
        jobMetrics.recordDeletions(job, "authorization_code", authorizationCodeCount.toLong())
        val grantCount = grantRepository.deleteFinished()
        jobMetrics.recordDeletions(job, "grant", grantCount.toLong())
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
