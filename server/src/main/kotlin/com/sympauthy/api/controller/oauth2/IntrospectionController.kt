package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.IntrospectionController.Companion.OAUTH2_INTROSPECTION_ENDPOINT
import com.sympauthy.api.controller.oauth2.util.CLIENT_ID_PARAMETER_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.CLIENT_SECRET_PARAMETER_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.controller.oauth2.util.INVALID_CLIENT_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.TOKEN_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.TOKEN_TYPE_HINT_PARAMETER_DESCRIPTION
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.resource.oauth2.IntrospectionResource
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.publishedMembers
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import java.time.ZoneOffset

@Controller(OAUTH2_INTROSPECTION_ENDPOINT)
class IntrospectionController(
    @Inject private val tokenManager: TokenManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val clientAuthenticationUtil: ClientAuthenticationUtil,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    @Operation(
        description = "Introspect a token, RFC 7662 §2. The client authenticates with HTTP Basic authentication " +
            "or with `client_id` and `client_secret` in the body. A token that does not validate, has expired " +
            "or been revoked, or was issued to another client is answered as inactive.",
        tags = ["oauth2"],
        parameters = [
            Parameter(
                name = TOKEN_PARAM,
                description = "Access token or refresh token to introspect, RFC 7662 §2.1.",
                schema = Schema(type = "string"),
                required = true
            ),
            Parameter(
                name = TOKEN_TYPE_HINT_PARAM,
                description = TOKEN_TYPE_HINT_PARAMETER_DESCRIPTION,
                schema = Schema(type = "string", allowableValues = ["access_token", "refresh_token"])
            ),
            Parameter(
                name = "client_id",
                description = CLIENT_ID_PARAMETER_DESCRIPTION,
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "client_secret",
                description = CLIENT_SECRET_PARAMETER_DESCRIPTION,
                schema = Schema(type = "string")
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Information about the token, or `active` alone when the token is inactive.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = TOKEN_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = INVALID_CLIENT_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        externalDocs = ExternalDocumentation(
            description = "RFC 7662",
            url = "https://datatracker.ietf.org/doc/html/rfc7662"
        )
    )
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Post(consumes = [APPLICATION_FORM_URLENCODED])
    suspend fun introspectToken(
        request: HttpRequest<*>,
        @Part(TOKEN_PARAM) token: String?,
        @Part(TOKEN_TYPE_HINT_PARAM) tokenTypeHint: String?,
        @Part("client_id") clientId: String?,
        @Part("client_secret") clientSecret: String?,
    ): IntrospectionResource {
        val client = clientAuthenticationUtil.resolveClient(request, clientId, clientSecret)
        if (token.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to TOKEN_PARAM)
        }

        val authConfig = uncheckedAuthConfig.orThrow()
        val authenticationToken = tokenManager.introspectToken(client, token, tokenTypeHint)
            ?: return IntrospectionResource(active = false)

        val scopes = authenticationToken.allScopes
        return IntrospectionResource(
            active = true,
            scope = scopes.joinToString(" ").ifEmpty { null },
            clientId = authenticationToken.clientId,
            tokenType = if (authenticationToken.dpopJkt != null) "DPoP" else "Bearer",
            exp = authenticationToken.expirationDate?.toEpochSecond(ZoneOffset.UTC),
            iat = authenticationToken.issueDate.toEpochSecond(ZoneOffset.UTC),
            authTime = authenticationToken.authenticationDate?.toEpochSecond(ZoneOffset.UTC),
            sub = authenticationToken.userId?.toString() ?: authenticationToken.clientId,
            aud = client.audience.tokenAudience,
            iss = authConfig.issuer,
            jti = authenticationToken.id.toString(),
            additionalClaims = publishedClaimMembers(authenticationToken, client)
        )
    }

    /**
     * The claims of the person the [token] was issued for that this response carries: the ones the
     * [client] may read and the deployment publishes in the introspection response, as the members they
     * travel as.
     *
     * A member [IntrospectionResource] declares itself is left out rather than written twice, so a claim a
     * deployment named after one of RFC 7662's own members never displaces it.
     *
     * Empty for a token no person is behind, which is a `client_credentials` token: there are no claims of
     * anybody to answer.
     */
    private suspend fun publishedClaimMembers(
        token: AuthenticationToken,
        client: Client
    ): Map<String, Any> {
        val userId = token.userId ?: return emptyMap()
        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
            userId = userId,
            audienceId = client.audience.id,
            place = ClaimPublicationPlace.INTROSPECTION,
            consentedScopes = token.consentedScopes,
            clientScopes = token.clientScopes
        )
        return claims.publishedMembers(reserved = IntrospectionResource.DECLARED_MEMBERS)
    }

    companion object {
        const val OAUTH2_INTROSPECTION_ENDPOINT = "/api/oauth2/introspect"
        const val TOKEN_PARAM = "token"
        const val TOKEN_TYPE_HINT_PARAM = "token_type_hint"
    }
}
