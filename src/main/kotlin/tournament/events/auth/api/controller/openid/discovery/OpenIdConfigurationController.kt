package tournament.events.auth.api.controller.openid.discovery

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject
import tournament.events.auth.api.controller.oauth2.AuthorizeController.Companion.OAUTH2_AUTHORIZE_ENDPOINT
import tournament.events.auth.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import tournament.events.auth.api.controller.openid.OpenIdUserInfoController.Companion.OPENID_USERINFO_ENDPOINT
import tournament.events.auth.api.controller.openid.discovery.PublicKeySetController.Companion.OPENID_JWKS_ENDPOINT
import tournament.events.auth.api.resource.openid.OpenIdConfigurationResource
import tournament.events.auth.business.model.user.StandardClaim
import tournament.events.auth.business.model.user.StandardScope
import tournament.events.auth.config.model.AuthConfig
import tournament.events.auth.config.model.UrlsConfig
import tournament.events.auth.config.model.getUri
import tournament.events.auth.config.model.orThrow

@Secured(IS_ANONYMOUS)
@Controller("/.well-known/openid-configuration")
class OpenIdConfigurationController(
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Operation(
        description = "Return the configuration of this OpenID provider.",
        tags = ["openiddiscovery"]
    )
    @Get
    fun getConfiguration(): OpenIdConfigurationResource {
        val authConfig = uncheckedAuthConfig.orThrow()
        val urlsConfig = uncheckedUrlsConfig.orThrow()
        return OpenIdConfigurationResource(
            issuer = authConfig.issuer,
            authorizationEndpoint = urlsConfig.getUri(OAUTH2_AUTHORIZE_ENDPOINT).toString(),
            tokenEndpoint = urlsConfig.getUri(OAUTH2_TOKEN_ENDPOINT).toString(),
            userInfoEndpoint = urlsConfig.getUri(OPENID_USERINFO_ENDPOINT).toString(),
            jwksUri = urlsConfig.getUri(OPENID_JWKS_ENDPOINT).toString(),
            scopesSupported = StandardScope.entries.map(StandardScope::id),
            responseTypesSupported = listOf("code", "id_token", "token id_token"),
            grantTypesSupported = listOf("authorization_code", "refresh_token"),
            subjectTypesSupported = listOf("public"),
            idTokenSigningAlgValuesSupported = listOf("RS256"),
            tokenEndpointAuthMethodsSupported = listOf("client_secret_basic"),
            claimsSupported = StandardClaim.entries.map(StandardClaim::id)
        )
    }
}
