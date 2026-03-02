package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.RevokeController.Companion.OAUTH2_REVOKE_ENDPOINT
import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import io.micronaut.http.HttpResponse
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
import jakarta.inject.Inject

@Controller(OAUTH2_REVOKE_ENDPOINT)
class RevokeController(
    @Inject private val tokenManager: TokenManager,
    @Inject private val clientAuthenticationUtil: ClientAuthenticationUtil
) {

    @Operation(
        description = """
Revoke an access or refresh token per RFC 7009.

Per the specification, the endpoint always returns HTTP 200, even if the token is invalid, already revoked, or does not exist.

If a refresh token is revoked, all access tokens associated with the same authorization session are also revoked (cascading revocation).

Client authentication is supported via:
- HTTP Basic Auth (`Authorization: Basic <base64(client_id:client_secret)>`)
- Form body parameters (`client_id` / `client_secret`)
""",
        tags = ["oauth2"],
        parameters = [
            Parameter(
                name = TOKEN_PARAM,
                description = "The token to revoke.",
                schema = Schema(type = "string"),
                required = true
            ),
            Parameter(
                name = TOKEN_TYPE_HINT_PARAM,
                description = "Optional hint about the type of the token submitted for revocation. Supported values are `access_token` and `refresh_token`.",
                schema = Schema(type = "string", allowableValues = ["access_token", "refresh_token"])
            ),
            Parameter(
                name = "client_id",
                description = "The client identifier. Used when client credentials are passed in the request body instead of via Basic Auth.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "client_secret",
                description = "The client secret. Used when client credentials are passed in the request body instead of via Basic Auth.",
                schema = Schema(type = "string")
            )
        ],
        externalDocs = ExternalDocumentation(
            description = "Token Revocation specification (RFC 7009)",
            url = "https://datatracker.ietf.org/doc/html/rfc7009"
        )
    )
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Post(consumes = [APPLICATION_FORM_URLENCODED])
    suspend fun revokeToken(
        request: HttpRequest<*>,
        @Part(TOKEN_PARAM) token: String?,
        @Part(TOKEN_TYPE_HINT_PARAM) tokenTypeHint: String?,
        @Part("client_id") clientId: String?,
        @Part("client_secret") clientSecret: String?,
    ): HttpResponse<Unit> {
        val client = clientAuthenticationUtil.resolveClient(request, clientId, clientSecret)
        if (token.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.missing_param", "param" to TOKEN_PARAM)
        }
        tokenManager.revokeTokenByEncodedToken(client, token, tokenTypeHint)
        return HttpResponse.ok()
    }

    companion object {
        const val OAUTH2_REVOKE_ENDPOINT = "/api/oauth2/revoke"
        const val TOKEN_PARAM = "token"
        const val TOKEN_TYPE_HINT_PARAM = "token_type_hint"
    }
}
