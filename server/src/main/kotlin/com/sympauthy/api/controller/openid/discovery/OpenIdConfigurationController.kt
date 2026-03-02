package com.sympauthy.api.controller.openid.discovery

import com.sympauthy.api.controller.oauth2.AuthorizeController.Companion.OAUTH2_AUTHORIZE_ENDPOINT
import com.sympauthy.api.controller.oauth2.RevokeController.Companion.OAUTH2_REVOKE_ENDPOINT
import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.controller.openid.OpenIdUserInfoController.Companion.OPENID_USERINFO_ENDPOINT
import com.sympauthy.api.controller.openid.discovery.PublicKeySetController.Companion.OPENID_JWKS_ENDPOINT
import com.sympauthy.api.resource.openid.OpenIdConfigurationResource
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject

@Secured(IS_ANONYMOUS)
@Controller("/.well-known/openid-configuration")
class OpenIdConfigurationController(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Operation(
        description = "Return the configuration of this OpenID provider.",
        tags = ["openiddiscovery"]
    )
    @Get
    suspend fun getConfiguration(): OpenIdConfigurationResource {
        val authConfig = uncheckedAuthConfig.orThrow()
        val urlsConfig = uncheckedUrlsConfig.orThrow()

        val scopes = scopeManager.listScopes()
            .filter { it.discoverable }
            .map { it.scope }
        val claims = claimManager.listStandardClaims()
            .flatMap { listOfNotNull(it.id, it.verifiedId) }

        return OpenIdConfigurationResource(
            issuer = authConfig.issuer,
            authorizationEndpoint = urlsConfig.getUri(OAUTH2_AUTHORIZE_ENDPOINT).toString(),
            tokenEndpoint = urlsConfig.getUri(OAUTH2_TOKEN_ENDPOINT).toString(),
            userInfoEndpoint = urlsConfig.getUri(OPENID_USERINFO_ENDPOINT).toString(),
            jwksUri = urlsConfig.getUri(OPENID_JWKS_ENDPOINT).toString(),
            revocationEndpoint = urlsConfig.getUri(OAUTH2_REVOKE_ENDPOINT).toString(),
            scopesSupported = scopes,
            responseTypesSupported = listOf("code", "id_token", "token id_token"),
            grantTypesSupported = listOf("authorization_code", "refresh_token", "client_credentials"),
            subjectTypesSupported = listOf("public"),
            idTokenSigningAlgValuesSupported = listOf("RS256"),
            tokenEndpointAuthMethodsSupported = listOf("client_secret_basic", "client_secret_post"),
            claimsSupported = claims
        )
    }
}
