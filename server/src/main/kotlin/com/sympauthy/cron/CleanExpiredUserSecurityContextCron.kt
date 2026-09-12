package com.sympauthy.cron

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.business.manager.lock.LeasedJob.CLEAN_EXPIRED_USER_SECURITY_CONTEXTS
import com.sympauthy.business.manager.security.UserSecurityContextCleaner
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Deletes the places nobody has signed in from for longer than the deployment keeps them.
 *
 * A job of its own rather than a step of [CleanExpiredInteractiveFlowSessionCron]: that transaction exists
 * to delete a session's dependants, and these rows deliberately outlive the sessions that observed them.
 *
 * The lease keeps a deployment of several instances from sweeping several times over, and the sweep stays
 * correct when it runs twice anyway — it deletes what is committed-expired, which is the same set on every
 * instance. [JobLeaseManager] carries why a leased job has to be one of those.
 */
@Singleton
class CleanExpiredUserSecurityContextCron(
    @Inject private val userSecurityContextCleaner: UserSecurityContextCleaner,
    @Inject private val jobLeaseManager: JobLeaseManager
) {
    private val logger = loggerForClass()

    @OptIn(DelicateCoroutinesApi::class)
    @Scheduled(fixedDelay = "15m")
    fun clean() {
        GlobalScope.launch {
            jobLeaseManager.withLease(CLEAN_EXPIRED_USER_SECURITY_CONTEXTS) {
                val count = userSecurityContextCleaner.clean()
                if (count > 0) {
                    logger.debug("Cleaned $count expired user security contexts.")
                }
            }
        }
    }
}
