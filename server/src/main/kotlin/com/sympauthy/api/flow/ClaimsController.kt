package com.sympauthy.api.flow

import com.sympauthy.api.FLOW_SURFACE
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.flow.mapper.ClaimsResourceMapper
import com.sympauthy.api.flow.mapper.ClientReferenceResourceMapper
import com.sympauthy.api.flow.resource.ClaimInputResource
import com.sympauthy.api.flow.resource.ClaimsFlowResource
import com.sympauthy.api.flow.resource.SimpleFlowResource
import com.sympauthy.api.flow.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.InteractiveFlowStepUriMapper
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.claim.manager.ConsentAwareClaimManager
import com.sympauthy.business.claim.manager.ConsentAwareCollectedClaimManager
import com.sympauthy.business.flow.oauth2.manager.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.provider.manager.ProviderClaimsManager
import com.sympauthy.business.securitycontext.model.ObservedRequest
import com.sympauthy.platform.util.orDefault
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
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
@Controller("$FLOW_SURFACE/claims")
class ClaimsController(
    @Inject private val consentAwareClaimManager: ConsentAwareClaimManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val claimsMapper: ClaimsResourceMapper,
    @Inject private val clientMapper: ClientReferenceResourceMapper,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
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
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
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
                    claimsMapper.toResource(
                        collectableClaims = collectableClaims,
                        collectedClaims = collectedClaims,
                        providerUserInfoList = providerUserInfoList,
                        client = clientMapper.toResourceOrNull(session),
                        cancelUrl = stepUriMapper.getCancelUriOrNull(session, authentication.stateOrNull)?.toString(),
                        locale = locale
                    )
                }
            },
            mapResultToResource = { it },
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
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION +
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
                    client = oauth2Manager.getClient(oauth2),
                    updates = collectedClaimUpdateMapper.toUpdates(inputResource.claims),
                    consentedScopes = oauth2.consentedScopes ?: emptyList()
                )
                session
            },
            mapRedirectUriToResource = { SimpleFlowResource(it.toString()) }
        )
}
