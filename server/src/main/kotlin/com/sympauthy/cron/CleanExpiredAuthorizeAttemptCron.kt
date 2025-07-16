package com.sympauthy.cron

import com.sympauthy.business.manager.auth.AuthorizeAttemptCleaner
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Singleton
class CleanExpiredAuthorizeAttemptCron(
    @Inject private val authorizeAttemptCleaner: AuthorizeAttemptCleaner
) {
    private val logger = loggerForClass()

    @OptIn(DelicateCoroutinesApi::class)
    @Scheduled(fixedDelay = "15m")
    fun clean() {
        // FIXME retrieve job and do not launch if job is still running
        GlobalScope.launch {
            // FIXME: Determine a leader that will execute the cleaning
            val result = authorizeAttemptCleaner.clean()
            if (result.authorizeAttemptCount > 0) {
                logger.debug("Cleaned ${result.authorizeAttemptCount} expired authorize attempts (including ${result.authorizationCodeCount} authorization codes, ${result.validationCodesCount} validation codes).")
            }
        }
    }
}
