package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientUserResourceMapper
import com.sympauthy.api.resource.client.ClientUserListResource
import com.sympauthy.api.resource.client.ClientUserResource
import com.sympauthy.api.util.COLLECTION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.COLLECTION_SEARCH_UNSUPPORTED_DESCRIPTION
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
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
    @Inject private val clientUserManager: ClientUserManager,
    @Inject private val userMapper: ClientUserResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve the users with an active consent for this client's audience. Users are ordered " +
            "by the date of their current consent, oldest first, and no other order can be asked for. An " +
            "end-user consenting again moves to the end, so a walk through the pages can skip a user or see " +
            "one twice.",
        tags = ["client"],
        parameters = [
            Parameter(
                name = "provider_id",
                `in` = ParameterIn.QUERY,
                description = "Keep the users linked to the provider with this identifier. When absent, any " +
                    "provider, or none.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "subject",
                `in` = ParameterIn.QUERY,
                description = "Keep the user whose subject at the provider named by `provider_id` is this " +
                    "value. It is refused without `provider_id`. When absent, any subject.",
                schema = Schema(type = "string")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of users."),
            ApiResponse(
                responseCode = "400",
                description = COLLECTION_BAD_REQUEST_DESCRIPTION + COLLECTION_SEARCH_UNSUPPORTED_DESCRIPTION +
                    "\n- `client.subject_without_provider`: `subject` is sent without `provider_id`."
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `users:read`."
            )
        ]
    )
    @Get
    suspend fun listUsers(
        request: HttpRequest<*>,
        authentication: Authentication,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?
    ): ClientUserListResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientAuth.client
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, clientUserManager.capabilities())
        val users = clientUserManager.listUsersForAudience(
            audience = client.audience,
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
        description = "Retrieve a user with an active consent for this client's audience.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "200", description = "The user."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `users:read`."
            ),
            ApiResponse(responseCode = "404", description = CLIENT_USER_NOT_FOUND_RESPONSE_DESCRIPTION)
        ]
    )
    @Get("/{userId}")
    suspend fun getUser(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID
    ): ClientUserResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientAuth.client
        val clientUser = clientUserManager.findUserForAudienceOrNull(client.audience.id, userId).orNotFound()
        return userMapper.toResource(clientUser)
    }
}
