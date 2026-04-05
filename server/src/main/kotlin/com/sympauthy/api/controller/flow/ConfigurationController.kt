package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_AUTHORIZE_ENDPOINT
import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.api.resource.flow.*
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowPasswordManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import com.sympauthy.server.DisplayMessages
import com.sympauthy.util.orDefault
import io.micronaut.context.MessageSource
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.*

@Secured(IS_ANONYMOUS)
@Controller("/api/v1/flow/configuration")
class ConfigurationController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val passwordFlowManager: WebAuthorizationFlowPasswordManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val uncheckedUrlsConfig: UrlsConfig,
    @Inject @param:DisplayMessages private val displayMessageSource: MessageSource
) {

    @Operation(
        description = """
Return the configuration of the authentication flow associated to the client. It exposes information such as:
- features that are enabled for the client.
- third party providers that can be used to authenticate the end-user.
- etc.

This configuration only contains information that are associated to this authorization server and the client,
they can be cached across the different end-users trying to authenticate.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getConfiguration(
        httpRequest: HttpRequest<*>
    ): ConfigurationResource = coroutineScope {
        val locale = httpRequest.locale.orDefault()
        val urlsConfig = uncheckedUrlsConfig.orThrow()

        val deferredClaims = async {
            getCollectableClaims(locale)
        }
        val features = getFeatures()
        val deferredProviders = async {
            providerManager.listEnabledProviders()
                .takeIf(List<EnabledProvider>::isNotEmpty)
                ?.map { getProvider(it, urlsConfig) }
        }

        ConfigurationResource(
            claims = deferredClaims.await(),
            features = features,
            password = getPassword(),
            providers = deferredProviders.await()
        )
    }

    private fun getCollectableClaims(locale: Locale): List<CollectableClaimConfigurationResource> {
        return claimManager.listCollectableClaims().map { claim ->
            CollectableClaimConfigurationResource(
                id = claim.id,
                required = claim.required,
                name = displayMessageSource.getMessage("claims.${claim.id}.name", claim.id, locale),
                group = claim.group?.name?.lowercase(),
                type = claim.dataType.name.lowercase()
            )
        }
    }

    private fun getFeatures(): FeaturesResource {
        return FeaturesResource(
            passwordSignIn = passwordFlowManager.signInEnabled,
            signUp = passwordFlowManager.signUpEnabled
        )
    }

    private fun getPassword(): PasswordConfigurationResource {
        return PasswordConfigurationResource(
            identifierClaims = claimManager.listIdentifierClaims().map { it.id }
        )
    }

    private fun getProvider(
        provider: EnabledProvider,
        urlsConfig: EnabledUrlsConfig
    ): ProviderConfigurationResource {
        val authorizeUrl = urlsConfig.getUri(
            FLOW_PROVIDER_ENDPOINTS + FLOW_PROVIDER_AUTHORIZE_ENDPOINT,
            "providerId" to provider.id
        )
        return ProviderConfigurationResource(
            id = provider.id,
            name = provider.name,
            authorizeUrl = authorizeUrl.toString()
        )
    }
}
