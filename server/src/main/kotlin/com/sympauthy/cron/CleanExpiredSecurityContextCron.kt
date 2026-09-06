package com.sympauthy.cron

import com.sympauthy.business.manager.securitycontext.SecurityContextCleaner
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Singleton
class CleanExpiredSecurityContextCron(
    @Inject private val securityContextCleaner: SecurityContextCleaner
) {
    private val logger = loggerForClass()

    @OptIn(DelicateCoroutinesApi::class)
    @Scheduled(fixedDelay = "15m")
    fun clean() {
        GlobalScope.launch {
            val count = securityContextCleaner.clean()
            if (count > 0) {
                logger.debug("Deleted $count security contexts whose retention has run out.")
            }
        }
    }
}
