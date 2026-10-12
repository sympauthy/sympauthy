package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.flow.ClaimsResourceMapper
import com.sympauthy.api.resource.flow.ClaimInputResource
import com.sympauthy.api.resource.flow.ClaimsFlowResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_REDIRECT_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.user.ConsentAwareClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("/api/v1/flow/claims")
class ClaimsController(
    @Inject private val consentAwareClaimManager: ConsentAwareClaimManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val claimsMapper: ClaimsResourceMapper,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
) {

    @Operation(
        description = "Retrieve the claims step: the claims the flow collects from the end-user, each with the " +
            "value the user holds and one a provider suggests. It leaves out identifier claims, and claims " +
            "outside the scopes the end-user consented to. When there is no claim to collect, the response " +
            "carries only `redirect_url`. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The claims to collect, or the `redirect_url` to send the browser to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = FLOW_REDIRECT_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getCollectableClaims(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        httpRequest: HttpRequest<*>
    ): ClaimsFlowResource {
        val locale = httpRequest.locale.orDefault()
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, _ ->
                val collectableClaims = consentAwareClaimManager.listClaimsCollectedInFlowBySession(session)
                if (collectableClaims.isEmpty()) {
                    null
                } else {
                    val collectedClaims = consentAwareCollectedClaimManager.findBySession(session)
                    val providerUserInfoList = session.userId?.let {
                        providerClaimsManager.findByUserId(it)
                    } ?: emptyList()
                    Triple(collectableClaims, collectedClaims, providerUserInfoList)
                }
            },
            mapResultToResource = { (collectableClaims, collectedClaims, providerUserInfoList) ->
                claimsMapper.toResource(collectableClaims, collectedClaims, providerUserInfoList, locale)
            },
            mapRedirectUriToResource = { claimsMapper.toResource(it) },
        )
    }

    @Operation(
        description = "Save the claim values the end-user entered on the claims step. A value for a claim the " +
            "step does not collect is not saved. A `null` or empty value records that the end-user was asked " +
            "for the claim and gave none. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = FLOW_REDIRECT_RESPONSE_DESCRIPTION,
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_REDIRECT_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.claims.invalid`: one or more values are refused, and `properties` holds the " +
                    "failure of each by claim identifier.\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun collectClaims(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: ClaimInputResource
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, _, user ->
                val oauth2 = oauth2Manager.fetchOAuth2(session)
                consentAwareCollectedClaimManager.updateInFlow(
                    user = user,
                    audienceId = oauth2Manager.getAudienceId(oauth2),
                    updates = collectedClaimUpdateMapper.toUpdates(inputResource.claims),
                    consentedScopes = oauth2.consentedScopes ?: emptyList()
                )
                session
            },
            mapRedirectUriToResource = { SimpleFlowResource(it.toString()) }
        )
}
