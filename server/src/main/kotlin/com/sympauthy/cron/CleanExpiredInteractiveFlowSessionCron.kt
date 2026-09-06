package com.sympauthy.cron

import com.sympauthy.business.manager.flow.InteractiveFlowSessionCleaner
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class CleanExpiredInteractiveFlowSessionCron(
    @Inject private val interactiveFlowSessionCleaner: InteractiveFlowSessionCleaner
) {
    private val logger = loggerForClass()

    @Scheduled(fixedDelay = "15m")
    fun clean() {
        runBlocking {
            val result = interactiveFlowSessionCleaner.clean()
            if (result.sessionCount > 0) {
                logger.debug(
                    "Cleaned ${result.sessionCount} expired interactive flow sessions " +
                        "(including ${result.authorizationCodeCount} authorization codes, " +
                        "${result.validationCodesCount} validation codes)."
                )
            }
            if (result.moreToClean) {
                logger.info(
                    "The expired interactive flow session cleanup stopped at its configured batch size " +
                        "with sessions left to remove. The next run continues where this one stopped; " +
                        "raise advanced.cleanup.batch-size if the backlog never drains."
                )
            }
        }
    }
}
