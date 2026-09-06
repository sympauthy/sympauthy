package com.sympauthy.cron

import com.sympauthy.business.manager.user.ProvisionalAccountManager
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Collects the accounts an abandoned sign-up left half-created, on a schedule of its own rather than as a
 * step of [CleanExpiredInteractiveFlowSessionCron].
 *
 * The sweep keys on the session being gone rather than on the sessions one run expired, so it needs nothing
 * from the run that removed them and the two are free to overlap: a session expired in the same tick as this
 * one is collected on a later one, which is the lag that design was chosen to tolerate. See
 * [ProvisionalAccountManager.deleteAbandoned].
 */
@Singleton
class CleanAbandonedAccountCron(
    @Inject private val provisionalAccountManager: ProvisionalAccountManager
) {
    private val logger = loggerForClass()

    @OptIn(DelicateCoroutinesApi::class)
    @Scheduled(fixedDelay = "15m")
    fun clean() {
        GlobalScope.launch {
            val count = provisionalAccountManager.deleteAbandoned()
            if (count > 0) {
                logger.debug("Cleaned $count accounts left behind by an abandoned sign-up.")
            }
        }
    }
}
