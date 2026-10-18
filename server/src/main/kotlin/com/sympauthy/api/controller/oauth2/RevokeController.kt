package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.RevokeController.Companion.OAUTH2_REVOKE_ENDPOINT
import com.sympauthy.api.controller.oauth2.util.CLIENT_ID_PARAMETER_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.CLIENT_SECRET_PARAMETER_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.controller.oauth2.util.TOKEN_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.TOKEN_TYPE_HINT_PARAMETER_DESCRIPTION
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
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

@Controller(OAUTH2_REVOKE_ENDPOINT)
class RevokeController(
    @Inject private val tokenManager: TokenManager,
    @Inject private val clientAuthenticationUtil: ClientAuthenticationUtil
) {

    @Operation(
        description = "Revoke a token issued to the client, RFC 7009 §2. A confidential client authenticates with " +
            "HTTP Basic authentication or with `client_id` and `client_secret` in the body, and a public client " +
            "sends `client_id` alone. Revoking a token revokes the grant it was issued under, whether it is an " +
            "access token or a refresh token: every token issued for the same authorization, and every token " +
            "a token exchange obtained from one of them.",
        tags = ["oauth2"],
        parameters = [
            Parameter(
                name = TOKEN_PARAM,
                description = "Access token or refresh token to revoke, RFC 7009 §2.1.",
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
                description = CLIENT_ID_PARAMETER_DESCRIPTION + " A public client sends it alone.",
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
                description = "Empty body. Answered as well when the token does not validate, is already revoked " +
                    "or was issued to another client, which leaves it as it is."
            ),
            ApiResponse(
                responseCode = "400",
                description = TOKEN_BAD_REQUEST_DESCRIPTION + "\n" +
                    "- `invalid_client`: no client credentials are sent, or the ones sent in the body do not " +
                    "authenticate a client."
            ),
            ApiResponse(
                responseCode = "401",
                description = "- `invalid_client`: the client credentials sent with HTTP Basic authentication do " +
                    "not authenticate a client. The `WWW-Authenticate` header names the `Basic` scheme."
            ),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        externalDocs = ExternalDocumentation(
            description = "RFC 7009",
            url = "https://datatracker.ietf.org/doc/html/rfc7009"
        )
    )
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Post(consumes = [APPLICATION_FORM_URLENCODED])
    suspend fun revokeToken(
        request: HttpRequest<*>,
        @Part(TOKEN_PARAM) token: String?,
        @Part("client_id") clientId: String?,
        @Part("client_secret") clientSecret: String?,
    ): HttpResponse<Unit> {
        val client = clientAuthenticationUtil.resolveClientAllowingPublic(request, clientId, clientSecret)
        if (token.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to TOKEN_PARAM)
        }
        tokenManager.revokeTokenByEncodedToken(client, token)
        return HttpResponse.ok()
    }

    companion object {
        const val OAUTH2_REVOKE_ENDPOINT = "/api/oauth2/revoke"
        const val TOKEN_PARAM = "token"
        const val TOKEN_TYPE_HINT_PARAM = "token_type_hint"
    }
}
