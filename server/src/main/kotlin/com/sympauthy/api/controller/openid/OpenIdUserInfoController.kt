package com.sympauthy.api.controller.openid

import com.sympauthy.api.controller.openid.OpenIdUserInfoController.Companion.OPENID_USERINFO_ENDPOINT
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.mapper.UserInfoResourceMapper
import com.sympauthy.api.util.observedRequest
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.securitycontext.AccessReviewManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.business.model.securitycontext.AccessReviewReason
import com.sympauthy.security.SecurityRule.IS_USER
import com.sympauthy.security.consentedScopes
import com.sympauthy.security.userAuthentication
import com.sympauthy.security.userId
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus.UNAUTHORIZED
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
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val userInfoMapper: UserInfoResourceMapper,
    @Inject private val accessReviewManager: AccessReviewManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val tokenManager: TokenManager
) {

    @Operation(
        description = "Retrieves the consented OpenID claims about the logged-in subject. " +
                "Only standard OpenID claims are returned; custom claims are excluded because this endpoint " +
                "is not client-authenticated and custom claims are client-only.",
        tags = ["openid"],
        externalDocs = ExternalDocumentation(
            url = "https://openid.net/specs/openid-connect-core-1_0.html#UserInfo"
        )
    )
    @Get
    suspend fun getUserInfo(
        authentication: Authentication,
        httpRequest: HttpRequest<*>
    ): UserInfoResource {
        reviewAccessOrThrow(authentication, httpRequest)
        // Use ReadableByUser (not ReadableByClient) because this endpoint is only protected
        // by a bearer token without client authentication, so the caller may be the end-user directly.
        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByUser(
            userId = authentication.userId,
            consentedScopes = authentication.consentedScopes.map(Scope::scope)
        )
        return userInfoMapper.toResource(authentication.userId, claims)
    }

    /**
     * Refuse the request where the client the token was issued to reviewed the place it came from and
     * said to, revoking every token of that sign-in first where it said that.
     *
     * A refusal is a `401`: the token is no longer one this endpoint serves, and the caller is told
     * that rather than why. The review is what the client configured — a client with no access-review
     * webhook is not called, and this costs it nothing.
     */
    private suspend fun reviewAccessOrThrow(authentication: Authentication, httpRequest: HttpRequest<*>) {
        val token = authentication.userAuthentication.authenticationToken
        val client = clientManager.findClientByIdOrNull(token.clientId) ?: return
        val decision = accessReviewManager.reviewAccess(
            client = client,
            userId = authentication.userId,
            reason = AccessReviewReason.USERINFO,
            observedRequest = httpRequest.observedRequest()
        )
        when (decision) {
            AccessReviewDecision.ALLOW -> Unit
            AccessReviewDecision.DENY -> throw accessReviewRefusal()
            AccessReviewDecision.REVOKE_SESSION -> {
                token.sessionId?.let { tokenManager.revokeSessionTokens(it) }
                throw accessReviewRefusal()
            }
        }
    }

    private fun accessReviewRefusal() = httpExceptionOf(
        UNAUTHORIZED, "userinfo.access_review_denied", "description.userinfo.access_review_denied"
    )

    companion object {
        const val OPENID_USERINFO_ENDPOINT = "/api/openid/userinfo"
    }
}
