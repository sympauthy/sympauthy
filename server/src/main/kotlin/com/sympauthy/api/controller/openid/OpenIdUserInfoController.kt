package com.sympauthy.api.controller.openid

import com.sympauthy.api.controller.openid.OpenIdUserInfoController.Companion.OPENID_USERINFO_ENDPOINT
import com.sympauthy.api.mapper.UserInfoResourceMapper
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.business.manager.user.AggregatedClaimsManager
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.security.SecurityRule.IS_USER
import com.sympauthy.security.scopes
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
    @Inject private val aggregatedClaimsManager: AggregatedClaimsManager,
    @Inject private val userInfoMapper: UserInfoResourceMapper
) {

    @Operation(
        description = "Retrieves the consented OpenID claims about the logged-in subject.",
        tags = ["openid"],
        externalDocs = ExternalDocumentation(
            url = "https://openid.net/specs/openid-connect-core-1_0.html#UserInfo"
        )
    )
    @Get
    suspend fun getUserInfo(
        authentication: Authentication
    ): UserInfoResource {
        val userInfo = aggregatedClaimsManager.aggregateClaims(
            userId = authentication.userId,
            consentedScopes = authentication.scopes.map(Scope::scope)
        )
        return userInfoMapper.toResource(userInfo)
    }

    companion object {
        const val OPENID_USERINFO_ENDPOINT = "/api/openid/userinfo"
    }
}
