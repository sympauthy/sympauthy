package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.ADMIN_SURFACE
import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserProviderResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminUserProviderLinkInputResource
import com.sympauthy.api.resource.admin.AdminUserProviderLinkResource
import com.sympauthy.api.resource.admin.AdminUserProviderListResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.COLLECTION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.RETURN_URI_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.SEARCH_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.collection.UserProviderCollectionManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.security.SecurityRule.ADMIN_USERS_WRITE
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.Explode
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.enums.ParameterStyle
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("$ADMIN_SURFACE/users/{userId}/providers")
class AdminUserProviderController(
    @Inject private val userManager: UserManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val userProviderCollectionManager: UserProviderCollectionManager,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val clientRedirectUriManager: ClientRedirectUriManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val linkProviderManager: InteractiveFlowSessionLinkProviderManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
    @Inject private val userProviderMapper: AdminUserProviderResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil,
) {

    @Operation(
        description = "Retrieve the providers linked to a user. " +
                "Providers are ordered by the date they were linked, oldest first, unless another order is " +
                "asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "$ADMIN_SURFACE/users/{userId}/providers/capabilities.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = FILTER_PARAMETER_NAME,
                `in` = ParameterIn.QUERY,
                description = FILTER_PARAMETER_DESCRIPTION,
                style = ParameterStyle.FORM,
                explode = Explode.TRUE,
                schema = Schema(
                    type = "object",
                    additionalProperties = Schema.AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION,
                    additionalPropertiesSchema = String::class
                )
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of providers."),
            ApiResponse(responseCode = "400", description = COLLECTION_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:read`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no user has this identifier."
            )
        ]
    )
    @Get
    @Secured(ADMIN_USERS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
    suspend fun listProviders(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the identifier of the provider."
        ) q: String?
    ): AdminUserProviderListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, userProviderCollectionManager.capabilities(), sort, q)
        val user = userManager.findByIdOrNull(userId).orNotFound()
        val providers = userProviderCollectionManager.listUserProviders(user, criteria, pageParams)
        return AdminUserProviderListResource(
            providers = providers.items.map(userProviderMapper::toResource),
            page = providers.page,
            size = providers.size,
            total = providers.total
        )
    }

    @Operation(
        description = "Retrieve what the collection of a user's providers accepts: the fields it filters on " +
                "and the operators each accepts, the fields it orders on and searches, and its own order. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection of a user's providers accepts."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:read`."
            )
        ]
    )
    @Get("/capabilities")
    @Secured(ADMIN_USERS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
    suspend fun getUserProviderCapabilities(
        request: HttpRequest<*>,
        @PathVariable @Parameter(
            description = "Identifier of a user. It is not looked up: the answer is the same for every user."
        ) userId: UUID
    ): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(userProviderCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Remove the link between a user and a provider.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "204", description = "The provider was unlinked."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:write`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no user has the identifier `userId`, or the user is not linked to " +
                        "the provider `providerId`."
            )
        ]
    )
    @Delete("/{providerId}")
    @Secured(ADMIN_USERS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_WRITE])
    @Status(HttpStatus.NO_CONTENT)
    suspend fun unlinkProvider(
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Identifier of the provider to unlink.") providerId: String
    ) {
        userManager.findByIdOrNull(userId).orNotFound()
        val link = providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, providerId).orNotFound()
        providerClaimsManager.deleteProviderLink(link)
    }

    @Operation(
        description = "Start linking a provider to a user. Send the end-user's browser to `redirect_url`: " +
                "the end-user confirms a request made by an administrator, signs in again, signs in with the " +
                "provider, and is sent back to `return_uri`.",
        tags = ["admin"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Where to send the end-user's browser to link the provider.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = RETURN_URI_BAD_REQUEST_DESCRIPTION + "\n" +
                    PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION + "\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:write`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no user has the identifier `userId`, `client_id` is absent or names " +
                        "no client, or `providerId` names no enabled provider."
            ),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ]
    )
    @Post("/{providerId}/link")
    @Secured(ADMIN_USERS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_WRITE])
    suspend fun startLink(
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Identifier of the provider to link.") providerId: String,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body resource: AdminUserProviderLinkInputResource
    ): AdminUserProviderLinkResource {
        // Both the target user and the named client must exist.
        val user = userManager.findByIdOrNull(userId).orNotFound()
        val client = resource.clientId
            ?.let { clientManager.findClientByIdOrNull(it) }
            .orNotFound()

        // Fail fast (before creating any session) on an unknown or disabled provider. Like the unknown-user /
        // unknown-client cases above, an absent named resource is a 404 (the provider id is a path variable).
        providerManager.listEnabledProviders().find { it.id == providerId }.orNotFound()

        // Validate the return URI (and the optional cancel URI) against the named client's registered redirect
        // URIs to avoid open redirects. recoverable = true: a bad URI is a bad request (400), not a 500.
        val returnUri = clientRedirectUriManager.parseRequestedRedirectUri(
            client,
            resource.returnUri,
            recoverable = true
        )
        val cancelUri = resource.cancelUri
            ?.let { clientRedirectUriManager.parseRequestedRedirectUri(client, it, recoverable = true) }

        // Admin-initiated: pass a null client id so the confirmation shows "an administrator".
        val flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow()
        val session = linkProviderManager.startLinkProviderSession(
            user = user,
            providerId = providerId,
            returnUri = returnUri,
            flow = flow,
            initiatingClientId = null,
            cancelUri = cancelUri
        )
        interactiveAuthFlowSessionControllerUtil.observeStartedSession(session, observedRequest)

        // The resulting redirect URI already carries the signed state as a query parameter.
        val (steppedSession, step) = engine.advance(session)
        val redirectUri = stepUriMapper.toRedirectUri(steppedSession, flow, step)
        return AdminUserProviderLinkResource(
            redirectUrl = redirectUri.toString()
        )
    }
}
