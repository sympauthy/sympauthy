package com.sympauthy.business.manager.provider

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.flow.WebAuthorizationFlowOauth2ProviderManager
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.config.ProviderOauth2Config
import io.micronaut.http.HttpResponse
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ProviderManager(
    @Inject private val oauth2ProviderManager: WebAuthorizationFlowOauth2ProviderManager
) {

    /**
     * Return the URL where the end-user must be redirected to in order to initiate an authentication with
     * the [provider].
     */
    suspend fun authorizeWithProvider(
        authorizeAttempt: AuthorizeAttempt,
        provider: EnabledProvider
    ): HttpResponse<*> {
        return when {
            provider.auth is ProviderOauth2Config -> oauth2ProviderManager.authorizeWithProvider(
                authorizeAttempt = authorizeAttempt,
                provider = provider
            )

            else -> throw businessExceptionOf("exception.client.unsupported")
        }
    }
}

