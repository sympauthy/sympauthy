package com.sympauthy.cron

import com.sympauthy.business.manager.flow.InteractiveFlowSessionCleaner
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Singleton
class CleanExpiredInteractiveFlowSessionCron(
    @Inject private val interactiveFlowSessionCleaner: InteractiveFlowSessionCleaner
) {
    private val logger = loggerForClass()

    @OptIn(DelicateCoroutinesApi::class)
    @Scheduled(fixedDelay = "15m")
    @Suppress("MaxLineLength")
    fun clean() {
        GlobalScope.launch {
            val result = interactiveFlowSessionCleaner.clean()
            if (result.sessionCount > 0 || result.abandonedAccountCount > 0) {
                logger.debug("Cleaned ${result.sessionCount} expired interactive flow sessions (including ${result.authorizationCodeCount} authorization codes, ${result.validationCodesCount} validation codes) and ${result.abandonedAccountCount} accounts left behind by an abandoned sign-up.")
            }
        }
    }
}
