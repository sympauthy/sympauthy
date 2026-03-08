package com.sympauthy

import com.sympauthy.api.controller.openapi.OpenApiController.Companion.OPENAPI_ENDPOINT
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.CustomClaim
import com.sympauthy.business.model.user.claim.StandardClaim
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ConfigReadinessManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.config.model.AdminConfig
import com.sympauthy.config.model.EnabledAdminConfig
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getOrNull
import com.sympauthy.config.model.getUri
import com.sympauthy.server.ErrorMessages
import com.sympauthy.util.DEFAULT_ENVIRONMENT
import com.sympauthy.util.getKeyAndLocalizedMessage
import com.sympauthy.util.isDefaultActive
import com.sympauthy.util.loggerForClass
import com.sympauthy.view.AdminUiController.Companion.ADMIN_UI_ENDPOINT
import com.sympauthy.view.DefaultAuthorizationFlowController.Companion.USER_FLOW_ENDPOINT
import io.micronaut.context.MessageSource
import io.micronaut.context.env.Environment
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Print info messages in logs to inform the user about the state of the application and the content of its configuration.
 * Or print the error reported by the configurations if any has been detected.
 */
@Singleton
class ApplicationReadinessStatusPrinter(
    @Inject private val configReadinessManager: ConfigReadinessManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val scopeGrantingRuleManager: ScopeGrantingRuleManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig,
    @Inject private val adminConfig: AdminConfig,
    @Inject private val environment: Environment,
    @param:ErrorMessages @Inject private val messageSource: MessageSource,
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    override fun onApplicationEvent(event: ServiceReadyEvent) {
        runBlocking {
            launch {
                val configurationErrors = configReadinessManager.getConfigurationErrors()
                if (configurationErrors.isEmpty()) {
                    printReadyBanner()
                    printServingBanner()
                } else {
                    printErrorBanner(configurationErrors)
                }
            }
        }
    }

    private suspend fun printReadyBanner() {
        logger.info("SympAuthy is ready and has found the following elements in its configuration:")
        val enabledClaims = try {
            claimManager.listEnabledClaims()
        } catch (_: Throwable) {
            emptyList()
        }
        val standardClaimsCount = enabledClaims.count { it is StandardClaim }
        val customClaimsCount = enabledClaims.count { it is CustomClaim }
        logger.info("- ${enabledClaims.size} claim(s) ($standardClaimsCount standard, $customClaimsCount custom).")

        val scopes = try {
            scopeManager.listScopes()
        } catch (_: Throwable) {
            emptyList()
        }
        val standardScopesCount = scopes.count { !it.admin && it.discoverable }
        val adminScopesCount = scopes.count { it.admin }
        val customScopesCount = scopes.count { !it.admin && !it.discoverable }
        logger.info("- ${scopes.size} scope(s) ($standardScopesCount standard, $adminScopesCount admin, $customScopesCount custom).")

        val clientsCount = try {
            clientManager.listClients().size
        } catch (_: Throwable) {
            0
        }
        logger.info("- $clientsCount client(s).")

        val rulesCount = try {
            scopeGrantingRuleManager.listScopeGrantingRules().size
        } catch (_: Throwable) {
            0
        }
        logger.info("- $rulesCount rule(s).")

        val mfaConfig = uncheckedMfaConfig as? EnabledMfaConfig
        val mfaMethods = listOfNotNull("TOTP".takeIf { mfaConfig?.totp == true })
        if (mfaConfig == null || (!mfaConfig.required && mfaMethods.isEmpty())) {
            logger.info("- MFA disabled.")
        } else {
            val requiredLabel = if (mfaConfig.required) "required" else "optional"
            logger.info("- MFA enabled ($requiredLabel, ${mfaMethods.joinToString()}).")
        }
    }

    private fun printServingBanner() {
        val urlsConfig = uncheckedUrlsConfig.getOrNull() ?: return
        val entries = mutableListOf<Pair<String, String>>()

        entries.add("OpenAPI documentation" to urlsConfig.getUri(OPENAPI_ENDPOINT).toString())
        entries.add("Swagger UI" to urlsConfig.getUri("/swagger-ui").toString())
        entries.add("Default end-user flow" to urlsConfig.getUri(USER_FLOW_ENDPOINT).toString())

        val enabledAdminConfig = adminConfig as? EnabledAdminConfig
        if (enabledAdminConfig != null && enabledAdminConfig.enabled && enabledAdminConfig.integratedUi) {
            val enabledUrlsConfig = uncheckedUrlsConfig as? EnabledUrlsConfig
            if (enabledUrlsConfig != null) {
                entries.add("Admin UI" to "${enabledUrlsConfig.root}${ADMIN_UI_ENDPOINT}")
            }
        }

        if (entries.isNotEmpty()) {
            logger.info("SympAuthy is currently serving:")
            entries.forEach { (label, url) ->
                logger.info("- $label: $url")
            }
        }
    }

    private suspend fun printErrorBanner(configurationErrors: List<Exception>) {
        if (configurationErrors.isEmpty()) {
            logger.info("No error detected in the configuration.")
        } else {
            logger.error("One or more errors detected in the configuration. This application will NOT OPERATE PROPERLY.")
            configurationErrors
                .map { it.getKeyAndLocalizedMessage(messageSource) }
                .forEach { (key, localizedErrorMessage) ->
                    logger.error("- $key: $localizedErrorMessage")
                }

            if (!environment.isDefaultActive) {
                logger.info("The '${DEFAULT_ENVIRONMENT}' environment is not enabled meaning you are missing default configuration of SympAuthy. If it is not intentional, you can enable it by adding '${DEFAULT_ENVIRONMENT}' to micronaut environments. Either by param '--micronaut-environments=${DEFAULT_ENVIRONMENT}' or by environment variable 'MICRONAUT_ENVIRONMENTS=${DEFAULT_ENVIRONMENT}'.")
            }
        }
    }
}
