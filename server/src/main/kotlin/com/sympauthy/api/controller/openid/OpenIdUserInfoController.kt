package com.sympauthy.api.controller.openid

import com.sympauthy.api.controller.openid.OpenIdUserInfoController.Companion.OPENID_USERINFO_ENDPOINT
import com.sympauthy.api.mapper.UserInfoResourceMapper
import com.sympauthy.api.resource.openid.UserInfoResource
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
import jakarta.inject.Inject

@Controller(OPENID_USERINFO_ENDPOINT)
@Secured(IS_USER)
class OpenIdUserInfoController(
    @Inject private val clientManager: ClientManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val userInfoMapper: UserInfoResourceMapper
) {

    @Operation(
        description = "Retrieves the claims about the logged-in subject that the end-user consented to " +
                "share and that this deployment publishes here. A claim the deployment configured itself " +
                "is returned under the identifier it was configured with, beside the standard " +
                "properties.\n\n" +
                "A claim named in the `userinfo` member of the `claims` parameter sent to /authorize is " +
                "returned here too, where this deployment configured that claim as askable in " +
                "/userinfo. The access token used here carries that request, so every token descended " +
                "from the same authorization — including one obtained by a refresh — answers the same " +
                "set.",
        tags = ["openid"],
        externalDocs = ExternalDocumentation(
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
