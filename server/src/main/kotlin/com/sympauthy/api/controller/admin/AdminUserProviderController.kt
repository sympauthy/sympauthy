package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.resource.admin.AdminUserProviderLinkInputResource
import com.sympauthy.api.resource.admin.AdminUserProviderLinkResource
import com.sympauthy.api.resource.admin.AdminUserProviderListResource
import com.sympauthy.api.resource.admin.AdminUserProviderResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.security.SecurityRule.ADMIN_USERS_WRITE
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
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val clientRedirectUriManager: ClientRedirectUriManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val linkProviderManager: InteractiveFlowSessionLinkProviderManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val paginationUtil: PaginationUtil,
) {

    @Operation(
        description = "Retrieve a paginated list of external identity providers linked to a user.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of linked providers."),
            ApiResponse(responseCode = "400", description = "Invalid page or size."),
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
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(description = "Number of results per page.") size: Int?
    ): AdminUserProviderListResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val (page, size) = paginationUtil.resolvePageParams(page, size)
        val allProviders = providerClaimsManager.findByUserId(userId)
        val paged = allProviders
            .drop(page * size)
            .take(size)
            .map {
                AdminUserProviderResource(
                    providerId = it.providerId,
                    subject = it.userInfo.subject,
                    linkedAt = it.linkDate
                )
            }
        return AdminUserProviderListResource(
            providers = paged,
            page = page,
            size = size,
            total = allProviders.size
        )
    }

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
        val returnUri = clientRedirectUriManager.parseRequestedRedirectUri(client, resource.returnUri, recoverable = true)
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

        // The resulting redirect URI already carries the signed state as a query parameter.
        val (steppedSession, step) = engine.advance(session)
        val redirectUri = stepUriMapper.toRedirectUri(steppedSession, flow, step)
        return AdminUserProviderLinkResource(
            redirectUrl = redirectUri.toString()
        )
    }
}
