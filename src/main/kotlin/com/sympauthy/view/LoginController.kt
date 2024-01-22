package com.sympauthy.view

import com.sympauthy.business.manager.auth.oauth2.AuthorizeManager
import com.sympauthy.business.manager.provider.ProviderConfigManager
import com.sympauthy.business.model.provider.EnabledProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.micronaut.views.View
import jakarta.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Controller("/login")
@Secured(IS_ANONYMOUS)
class LoginController(
    @Inject private val authorizeManager: AuthorizeManager,
    @Inject private val providerConfigManager: ProviderConfigManager
) {

    @Get
    @View("login")
    suspend fun login(
        httpRequest: HttpRequest<*>,
        @QueryValue state: String?
    ): Map<String, *> = coroutineScope {
        authorizeManager.verifyEncodedState(state)

        val asyncClients = async {
            providerConfigManager.listEnabledProviders().toList()
                .sortedWith(compareBy(EnabledProvider::name))
        }

        mapOf(
            "state" to state,
            "providers" to asyncClients.await()
        )
    }
}