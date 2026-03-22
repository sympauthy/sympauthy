package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientUserResourceMapper
import com.sympauthy.api.resource.client.ClientUserListResource
import com.sympauthy.api.resource.client.ClientUserResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.user.ClientUserManager
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
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/client/users")
@Secured(CLIENT_USERS_READ)
class ClientUserController(
    @Inject private val clientUserManager: ClientUserManager,
    @Inject private val userMapper: ClientUserResourceMapper
) {

    @Operation(
        description = "Retrieve a paginated list of end-users who have granted scopes to the requesting client.",
        tags = ["client"],
        parameters = [
            Parameter(
                name = "page",
                description = "Zero-indexed page number.",
                schema = Schema(type = "integer", defaultValue = "0")
            ),
            Parameter(
                name = "size",
                description = "Number of results per page.",
                schema = Schema(type = "integer", defaultValue = "20")
            ),
            Parameter(
                name = "provider_id",
                description = "Filter users linked to a specific provider.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "subject",
                description = "Filter by provider subject ID. Must be used together with provider_id.",
                schema = Schema(type = "string")
            )
        ],
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
        @QueryValue page: Int?,
        @QueryValue size: Int?,
        @QueryValue("provider_id") providerId: String?,
        @QueryValue subject: String?
    ): ClientUserListResource {
        if (subject != null && providerId == null) {
            throw businessExceptionOf("client.subject_without_provider")
        }

        val clientAuth = authentication.clientAuthentication
        val (resolvedPage, resolvedSize) = resolvePageParams(page, size)
        val (users, total) = clientUserManager.listUsersForClient(
            clientId = clientAuth.clientId,
            providerId = providerId,
            subject = subject,
            page = resolvedPage,
            size = resolvedSize
        )

        return ClientUserListResource(
            users = users.map(userMapper::toResource),
            page = resolvedPage,
            size = resolvedSize,
            total = total
        )
    }

    @Operation(
        description = "Retrieve basic information about a specific user's authorization status.",
        tags = ["client"],
        parameters = [
            Parameter(
                name = "userId",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            )
        ],
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
        @PathVariable userId: UUID
    ): ClientUserResource {
        val clientAuth = authentication.clientAuthentication
        val clientUser = clientUserManager.findUserForClientOrNull(clientAuth.clientId, userId).orNotFound()
        return userMapper.toResource(clientUser)
    }
}
