package com.sympauthy.api.controller.openid

import com.sympauthy.api.controller.openid.OpenIdUserInfoController.Companion.OPENID_USERINFO_ENDPOINT
import com.sympauthy.api.mapper.UserInfoResourceMapper
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.security.SecurityRule.IS_USER
import com.sympauthy.security.consentedScopes
import com.sympauthy.security.userAuthentication
import com.sympauthy.security.userId
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Controller(OPENID_USERINFO_ENDPOINT)
@Secured(IS_USER)
class OpenIdUserInfoController(
    @Inject private val clientManager: ClientManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val userInfoMapper: UserInfoResourceMapper
) {

    @Operation(
        description = "Retrieve the claims of the end-user an access token was issued for, OpenID Connect Core " +
            "1.0 §5.3. The access token is sent as `Authorization: Bearer <token>`. The response holds the " +
            "claims the end-user consented to share that the configuration publishes in it: always " +
            "(`claims.<id>.published-in`), or when the `userinfo` member of the `claims` parameter of the " +
            "authorization request named them (`claims.<id>.published-in-when-requested`).",
        tags = ["openid"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Claims of the end-user.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "- `client.invalid_client_id`: the client the access token was issued to is no " +
                    "longer configured."
            ),
            ApiResponse(
                responseCode = "401",
                description = UNAUTHORIZED_RESPONSE_DESCRIPTION
            ),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token was issued by the `client_credentials` grant, " +
                    "with no end-user behind it."
            )
        ],
        externalDocs = ExternalDocumentation(
            description = "OpenID Connect Core 1.0, §5.3",
            url = "https://openid.net/specs/openid-connect-core-1_0.html#UserInfo"
        )
    )
    @Get
    suspend fun getUserInfo(
        authentication: Authentication
    ): UserInfoResource {
        val token = authentication.userAuthentication.authenticationToken
        val client = clientManager.findClientById(token.clientId)
        // The person's own read rather than the client's: this endpoint is protected by a bearer token
        // alone, with no client authentication, so consent is the whole of what it may go on.
        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByPerson(
            userId = authentication.userId,
            audienceId = client.audience.id,
            consentedScopes = authentication.consentedScopes.map(Scope::scope)
        )
        // Off the token rather than off a session: the authorization that carried the `claims` parameter
        // is long over, and every access token descended from it answers the request it made.
        return userInfoMapper.toResource(authentication.userId, claims, token.requestedClaims)
    }

    companion object {
        const val OPENID_USERINFO_ENDPOINT = "/api/openid/userinfo"
    }
}
