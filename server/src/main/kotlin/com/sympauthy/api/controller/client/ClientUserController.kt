package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientUserResourceMapper
import com.sympauthy.api.resource.client.ClientUserListResource
import com.sympauthy.api.resource.client.ClientUserResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.user.ClientUserManager
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.security.SecurityRule.CLIENT_USERS_READ
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/client/users")
@Secured(CLIENT_USERS_READ)
@SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.USERS_READ])
class ClientUserController(
    @Inject private val clientManager: ClientManager,
    @Inject private val clientUserManager: ClientUserManager,
    @Inject private val userMapper: ClientUserResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve a paginated list of end-users who have granted scopes to the requesting client. " +
                "Users are ordered by the date of their current consent, oldest first. That date is rewritten " +
                "each time a user authorizes again, which moves them to the end of the list, so a client " +
                "walking every page while users are signing in may miss one or see one twice.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of users."),
            ApiResponse(responseCode = "400", description = "Invalid query parameters."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: users:read."
            )
        ]
    )
    @Get
    suspend fun listUsers(
        authentication: Authentication,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue("provider_id") @Parameter(description = "Filter users linked to a specific provider.") providerId: String?,
        @QueryValue @Parameter(description = "Filter by provider subject ID. Must be used together with provider_id.") subject: String?
    ): ClientUserListResource {
        if (subject != null && providerId == null) {
            throw recoverableBusinessExceptionOf(
                "client.subject_without_provider",
                "description.client.subject_without_provider"
            )
        }

        val clientAuth = authentication.clientAuthentication
        val client = clientManager.findClientById(clientAuth.clientId)
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val users = clientUserManager.listUsersForAudience(
            audienceId = client.audience.id,
            providerId = providerId,
            subject = subject,
            pageParams = pageParams
        )

        return ClientUserListResource(
            users = users.items.map(userMapper::toResource),
            page = users.page,
            size = users.size,
            total = users.total
        )
    }

    @Operation(
        description = "Retrieve basic information about a specific user's authorization status.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "200", description = "User information."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: users:read."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Get("/{userId}")
    suspend fun getUser(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID
    ): ClientUserResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientManager.findClientById(clientAuth.clientId)
        val clientUser = clientUserManager.findUserForAudienceOrNull(client.audience.id, userId).orNotFound()
        return userMapper.toResource(clientUser)
    }
}
