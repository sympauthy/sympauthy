package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientUserResourceMapper
import com.sympauthy.api.resource.client.ClientUserListResource
import com.sympauthy.api.resource.client.ClientUserResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.user.ClientUserManager
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.security.SecurityRule.CLIENT_USERS_READ
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
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
                "walking every page while users are signing in may miss one or see one twice. " +
                "They can be filtered on provider_id, and on subject together with it; each is an exact " +
                "match and accepts no other operator. This collection is not ordered or searched by a " +
                "caller.",
        tags = ["client"],
        parameters = [
            Parameter(
                name = "provider_id",
                `in` = ParameterIn.QUERY,
                description = "Keep the users linked to the provider this names.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "subject",
                `in` = ParameterIn.QUERY,
                description = "Keep the user the provider named by provider_id knows under this " +
                        "subject. It identifies nobody on its own, so it is refused without one.",
                schema = Schema(type = "string")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of users."),
            ApiResponse(
                responseCode = "400",
                description = "Invalid page or size, an unknown field, an operator the field does not " +
                        "accept, or a subject sent without a provider_id."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: users:read."
            )
        ]
    )
    @Get
    suspend fun listUsers(
        request: HttpRequest<*>,
        authentication: Authentication,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?
    ): ClientUserListResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientManager.findClientById(clientAuth.clientId)
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, clientUserManager.capabilities())
        val users = clientUserManager.listUsersForAudience(
            audienceId = client.audience.id,
            criteria = criteria,
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
