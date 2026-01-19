package com.sympauthy.api.controller.flow

import com.sympauthy.api.mapper.flow.FlowErrorResourceMapper
import com.sympauthy.api.resource.flow.FlowErrorResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("/api/v1/flow/errors")
class ErrorController(
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder,
    @Inject private val flowErrorResourceMapper: FlowErrorResourceMapper,
) {

    @Operation(
        description = "Get details about the error that caused the authentication flow to fail.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = """
Result containing either:
- details about the error that caused the authentication flow to fail.
- a redirect uri for the end-user to continue the authorization flow if there is no error.
""",
            ),
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getError(
        request: HttpRequest<*>,
        authentication: Authentication
    ): FlowErrorResource {
        return try {
            webAuthorizationFlowManager.extractFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, flow ->
                if (authorizeAttempt.errorDetailsId == null) {
                    val result = webAuthorizationFlowManager.getStatusAndCompleteIfNecessary(
                        authorizeAttempt = authorizeAttempt,
                    )
                    val redirectUri = redirectUriBuilder.getRedirectUri(
                        authorizeAttempt = authorizeAttempt,
                        flow = flow,
                        status = result
                    )
                    flowErrorResourceMapper.toResource(redirectUri)
                } else {
                    val exception = BusinessException(
                        recoverable = false,
                        detailsId = authorizeAttempt.errorDetailsId,
                        descriptionId = authorizeAttempt.errorDescriptionId,
                    )
                    flowErrorResourceMapper.toResource(exception, request.locale.orDefault())
                }
            }
        } catch (e: BusinessException) {
            flowErrorResourceMapper.toResource(e, request.locale.orDefault())
        }
    }
}
