package com.sympauthy

import com.sympauthy.api.controller.openapi.OpenApiController.Companion.OPENAPI_ENDPOINT
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.CustomClaim
import com.sympauthy.business.model.user.claim.StandardClaim
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ConfigReadinessManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.provider.ProviderConfigManager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.business.model.oauth2.isAdmin
import com.sympauthy.config.model.AdminConfig
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.EnabledAdminConfig
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getOrNull
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
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
    @Inject private val providerConfigManager: ProviderConfigManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
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
        val authConfig = uncheckedAuthConfig.orThrow()
        logger.info("- Issuer: ${authConfig.issuer} / Audience: ${authConfig.audience}")

        val byPasswordLabel = if (authConfig.byPassword.enabled) "enabled" else "disabled"
        logger.info("- Authentication by password: $byPasswordLabel.")
        val enabledProviders = try {
            providerConfigManager.listEnabledProviders()
        } catch (_: Throwable) {
            emptyList()
        }
        if (enabledProviders.isEmpty()) {
            logger.info("- Authentication by provider: disabled.")
        } else {
            logger.info("- Authentication by provider: enabled (${pluralize(enabledProviders.size, "provider")}).")
        }

        val mfaConfig = uncheckedMfaConfig as? EnabledMfaConfig
        val mfaMethods = listOfNotNull("TOTP".takeIf { mfaConfig?.totp == true })
        if (mfaConfig == null || (!mfaConfig.required && mfaMethods.isEmpty())) {
            logger.info("- MFA disabled.")
        } else {
            val requiredLabel = if (mfaConfig.required) "required" else "optional"
            logger.info("- MFA enabled ($requiredLabel, ${mfaMethods.joinToString()}).")
        }

        val enabledClaims = try {
            claimManager.listEnabledClaims()
        } catch (_: Throwable) {
            emptyList()
        }
        val standardClaimsCount = enabledClaims.count { it is StandardClaim }
        val customClaimsCount = enabledClaims.count { it is CustomClaim }
        logger.info("- ${pluralize(enabledClaims.size, "claim")} (${pluralize(standardClaimsCount, "standard")}, ${pluralize(customClaimsCount, "custom")}).")

        val scopes = try {
            scopeManager.listScopes()
        } catch (_: Throwable) {
            emptyList()
        }
        val consentableScopesCount = scopes.count { it is com.sympauthy.business.model.oauth2.ConsentableUserScope }
        val adminScopesCount = scopes.count { it is com.sympauthy.business.model.oauth2.GrantableUserScope && it.isAdmin }
        val grantableScopesCount = scopes.count { it is com.sympauthy.business.model.oauth2.GrantableUserScope && !it.isAdmin }
        val clientScopesCount = scopes.count { it is com.sympauthy.business.model.oauth2.ClientScope }
        logger.info("- ${pluralize(scopes.size, "scope")} (${pluralize(consentableScopesCount, "consentable")}, ${pluralize(grantableScopesCount, "grantable")}, ${pluralize(adminScopesCount, "admin")}, ${pluralize(clientScopesCount, "client")}).")

        val clientsCount = try {
            clientManager.listClients().size
        } catch (_: Throwable) {
            0
        }
        logger.info("- ${pluralize(clientsCount, "client")}.")

        val rulesCount = try {
            scopeGrantingRuleManager.listScopeGrantingRules().size
        } catch (_: Throwable) {
            0
        }
        logger.info("- ${pluralize(rulesCount, "rule")}.")
    }

    private fun pluralize(count: Int, singular: String) = if (count <= 1) "$count $singular" else "$count ${singular}s"

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
