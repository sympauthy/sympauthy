package com.sympauthy.api.controller.openid.discovery

import com.sympauthy.api.controller.oauth2.AuthorizeController.Companion.OAUTH2_AUTHORIZE_ENDPOINT
import com.sympauthy.api.controller.oauth2.IntrospectionController.Companion.OAUTH2_INTROSPECTION_ENDPOINT
import com.sympauthy.api.controller.oauth2.RevokeController.Companion.OAUTH2_REVOKE_ENDPOINT
import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.controller.openid.OpenIdUserInfoController.Companion.OPENID_USERINFO_ENDPOINT
import com.sympauthy.api.controller.openid.discovery.PublicKeySetController.Companion.OPENID_JWKS_ENDPOINT
import com.sympauthy.api.resource.openid.OpenIdConfigurationResource
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.ResponseType
import com.sympauthy.config.model.*
import com.sympauthy.util.wireName
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject

/**
 * Serve the discovery document: what a client that has not been told what to ask for can find out
 * about this deployment.
 *
 * Every capability here is read from the set that serves it, so that changing what the server does
 * changes what it advertises. `response_types_supported` and `grant_types_supported` are
 * [ResponseType] and [GrantType], the closed sets the authorization and token endpoints branch on
 * exhaustively: a value added to either does not compile until its endpoint implements it. The
 * document is the contract a conforming client configures itself from, so a capability listed and
 * not served puts the failure on a correct client doing what it was told, and one served and not
 * listed cannot be found by a client that reads capabilities rather than documentation.
 *
 * The client authentication methods and the subject type are written out because nothing dispatches
 * on a closed set of either — [com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil]
 * tries the header and then the body rather than branching on a named method. An enum whose one
 * reader is this document would relocate those literals rather than derive them, so they are held
 * by their test instead.
 *
 * `scopes_supported` is the exception to all of it, and deliberately: the scope list says what a
 * client could ask for, and a scope is served whether or not it appears here.
 */
@Secured(IS_ANONYMOUS)
@Controller("/.well-known/openid-configuration")
class OpenIdConfigurationController(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig
) {

    @Operation(
        description = "Return the configuration of this OpenID provider.",
        tags = ["openiddiscovery"]
    )
    @Get
    suspend fun getConfiguration(): OpenIdConfigurationResource {
        val authConfig = uncheckedAuthConfig.orThrow()
        val urlsConfig = uncheckedUrlsConfig.orThrow()
        val advancedConfig = uncheckedAdvancedConfig.orThrow()

        val scopes = scopeManager.listEnabledScopes()
            .filter { it.discoverable }
            .map { it.scope }
        val claims = claimManager.listEnabledOpenIdConnectClaims()
            .flatMap { listOfNotNull(it.id, it.verifiedId) }

        return OpenIdConfigurationResource(
            issuer = authConfig.issuer,
            authorizationEndpoint = urlsConfig.getUri(OAUTH2_AUTHORIZE_ENDPOINT).toString(),
            tokenEndpoint = urlsConfig.getUri(OAUTH2_TOKEN_ENDPOINT).toString(),
            userInfoEndpoint = urlsConfig.getUri(OPENID_USERINFO_ENDPOINT).toString(),
            jwksUri = urlsConfig.getUri(OPENID_JWKS_ENDPOINT).toString(),
            revocationEndpoint = urlsConfig.getUri(OAUTH2_REVOKE_ENDPOINT).toString(),
            introspectionEndpoint = urlsConfig.getUri(OAUTH2_INTROSPECTION_ENDPOINT).toString(),
            introspectionEndpointAuthMethodsSupported = listOf("client_secret_basic", "client_secret_post"),
            scopesSupported = scopes,
            responseTypesSupported = ResponseType.entries.map { it.wireName },
            grantTypesSupported = GrantType.entries.map { it.wireName },
            subjectTypesSupported = listOf("public"),
            idTokenSigningAlgValuesSupported = listOf(advancedConfig.publicJwtAlgorithm.name),
            tokenEndpointAuthMethodsSupported = listOf("client_secret_basic", "client_secret_post"),
            claimsSupported = claims,
            codeChallengeMethodsSupported = CodeChallengeMethod.entries.map { it.value },
            dpopSigningAlgValuesSupported = DpopManager.SUPPORTED_ALGORITHMS.toList()
        )
    }
}
