package com.sympauthy.cron

import com.sympauthy.business.manager.user.ProvisionalAccountManager
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

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
    @Inject private val provisionalAccountManager: ProvisionalAccountManager,
    @Inject private val advancedConfig: AdvancedConfig
) {
    private val logger = loggerForClass()

    @Scheduled(fixedDelay = "15m")
    fun clean() {
        runBlocking {
            val result = provisionalAccountManager.deleteAbandoned(advancedConfig.orThrow().cleanup.batchSize)
            if (result.deletedCount > 0) {
                logger.debug("Cleaned ${result.deletedCount} accounts left behind by an abandoned sign-up.")
            }
            if (result.filledBatch) {
                logger.warn(
                    "The abandoned account sweep stopped at its configured batch size with accounts left " +
                        "to collect. The next run continues where this one stopped; raise " +
                        "advanced.cleanup.batch-size if the backlog never drains."
                )
            }
        }
    }
}
