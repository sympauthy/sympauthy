package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserProviderResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminUserProviderLinkInputResource
import com.sympauthy.api.resource.admin.AdminUserProviderLinkResource
import com.sympauthy.api.resource.admin.AdminUserProviderListResource
import com.sympauthy.api.util.PaginationUtil
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
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/providers")
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
        description = "Retrieve a paginated list of external identity providers linked to a user. Providers " +
                "are ordered by the date they were linked, oldest first, then by provider identifier, " +
                "unless another order is asked for. " +
                "Which fields this collection can be filtered, ordered and row on is published at " +
                "/api/v1/admin/users/{userId}/providers/capabilities.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of linked providers."),
            ApiResponse(
                responseCode = "400",
                description = "Invalid page or size, an unknown field, an operator the field does not " +
                        "accept, or a value it does not hold."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:read."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Get
    @Secured(ADMIN_USERS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
    suspend fun listProviders(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue @Parameter(
            description = "Comma-separated list of keys to order by, each prefixed with - to read it " +
                    "from the largest value to the smallest. The keys are published by the capabilities " +
                    "endpoint."
        ) sort: String?,
        @QueryValue @Parameter(
            description = "Partial case-insensitive search across the fields the capabilities endpoint " +
                    "publishes as searchable."
        ) q: String?
    ): AdminUserProviderListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, userProviderCollectionManager.capabilities(), sort, q)
        userManager.findByIdOrNull(userId).orNotFound()
        val providers = userProviderCollectionManager.listUserProviders(userId, criteria, pageParams)
        return AdminUserProviderListResource(
            providers = providers.items.map(userProviderMapper::toResource),
            page = providers.page,
            size = providers.size,
            total = providers.total
        )
    }

    @Operation(
        description = "Retrieve what the linked provider collection accepts: the fields it filters on and " +
                "the operators each admits, the fields it orders on, the fields a free-text search " +
                "matches against, and the order it takes when none is asked for. " +
                "It describes the collection rather than one account, so it reads the same for every user " +
                "identifier and looks none of them up. " +
                "The names it carries are read in the language the request asked for and may be reworded " +
                "in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection accepts."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:read."
            )
        ]
    )
    @Get("/capabilities")
    @Secured(ADMIN_USERS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
    suspend fun getUserProviderCapabilities(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID
    ): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(userProviderCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Remove the link between a user and an external identity provider.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "204", description = "Provider unlinked successfully."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:write."
            ),
            ApiResponse(
                responseCode = "404",
                description = "No user found with the given identifier, or no provider link found."
            )
        ]
    )
    @Delete("/{providerId}")
    @Secured(ADMIN_USERS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_WRITE])
    @Status(HttpStatus.NO_CONTENT)
    suspend fun unlinkProvider(
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Identifier of the provider to unlink.") providerId: String
    ) {
        userManager.findByIdOrNull(userId).orNotFound()
        providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, providerId).orNotFound()
        providerClaimsManager.deleteProviderLink(userId, providerId)
    }

    @Operation(
        description = "Start linking an identity provider to a given user, initiated by an administrator. " +
                "Validates that the user, the named client and the provider exist and that the return (and " +
                "optional cancel) URI are registered redirect URIs of that client, then creates a link session " +
                "gated by a confirmation the end-user must approve (shown as initiated by an administrator) and " +
                "a forced re-authentication (linking a provider mints a durable login credential). Returns the " +
                "redirect_url to hand or send to the user; once the link completes they are redirected to " +
                "return_uri.",
        tags = ["admin"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The link session was started.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid return or cancel URI."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:write."
            ),
            ApiResponse(
                responseCode = "404",
                description = "No user, client, or enabled provider found with the given identifier."
            )
        ]
    )
    @Post("/{providerId}/link")
    @Secured(ADMIN_USERS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_WRITE])
    suspend fun startLink(
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Identifier of the provider to link.") providerId: String,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body resource: AdminUserProviderLinkInputResource
    ): AdminUserProviderLinkResource {
        // Both the target user and the named client must exist.
        userManager.findByIdOrNull(userId).orNotFound()
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
            userId = userId,
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
