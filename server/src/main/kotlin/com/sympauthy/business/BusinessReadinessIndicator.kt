package com.sympauthy.business

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.util.loggerForClass
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Print info messages in logs to inform the user about the state of the configuration.
 */
@Singleton
class BusinessReadinessIndicator(
    @Inject private val claimManager: ClaimManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val scopeGrantingRuleManager: ScopeGrantingRuleManager,
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    override fun onApplicationEvent(event: ServiceReadyEvent) = runBlocking {
        logger.info("SympAuthy is ready and has found the following elements in its configuration:")
        try {
            val claims = claimManager.listStandardClaims()
            logger.info("- ${claims.size} claim(s).")

            val scopes = scopeManager.listScopes()
            logger.info("- ${claims.size} scope(s).")

            val clients = clientManager.listClients()
            logger.info("- ${clients.size} client(s).")

            val rules = scopeGrantingRuleManager.listScopeGrantingRules()
            logger.info("- ${rules.size} rule(s).")
        } catch (_: Throwable) {
            // Do not log since the errors will likely be reported by another components
        }
    }
}
