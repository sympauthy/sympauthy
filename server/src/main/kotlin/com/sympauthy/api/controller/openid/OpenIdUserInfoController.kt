package com.sympauthy.api.controller.openid

import com.sympauthy.api.controller.OPENID_SURFACE
import com.sympauthy.api.controller.openid.OpenIdUserInfoController.Companion.OPENID_USERINFO_ENDPOINT
import com.sympauthy.api.mapper.UserInfoResourceMapper
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.security.SecurityRule.IS_USER
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

/**
 * Gated on a user's access token rather than left anonymous like the rest of the protocol surface, because
 * OpenID Connect Core §5.3 has the endpoint called with the access token whose claims it returns.
 */
@Controller(OPENID_USERINFO_ENDPOINT)
@Secured(IS_USER)
class OpenIdUserInfoController(
    @Inject private val claimAclManager: ClaimAclManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val userInfoMapper: UserInfoResourceMapper
) {

    @Operation(
        description = "Retrieve the claims of the end-user an access token was issued for, OpenID Connect Core " +
            "1.0 §5.3. The response holds the claims the end-user consented to share that the configuration " +
            "publishes in it: always (`claims.<id>.published-in`), or when the `userinfo` member of the `claims` " +
            "parameter of the authorization request named them (`claims.<id>.published-in-when-requested`).",
        tags = ["openid"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Claims of the end-user.",
                useReturnTypeSchema = true
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
        val grant = authentication.userAuthentication.authenticationToken.grant
        // The person's own read rather than the client's: this endpoint is protected by a bearer token
        // alone, with no client authentication, so consent is the whole of what it may go on.
        // The audience the grant was opened for, which is the one the token was issued for, rather than the
        // one the configuration declares for the client now.
        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByPerson(
            userId = authentication.userId,
            audienceId = grant.audienceId,
            doors = claimAclManager.getTokenDoors(authentication.userAuthentication.claimAclContext())
        )
        // Off the grant rather than off a session: the authorization that carried the `claims` parameter
        // is long over, and every access token issued under its grant answers the request it made.
        // The account is not resolved here as other surfaces do: this is a protocol endpoint, whose
        // specification names the answer to a token that does not resolve, and the validated token is it.
        return userInfoMapper.toResource(authentication.userId, claims, grant.requestedClaims)
    }

    companion object {
        const val OPENID_USERINFO_ENDPOINT = "$OPENID_SURFACE/userinfo"
    }
}
