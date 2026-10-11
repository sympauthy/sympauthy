package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserDetailResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.api.resource.admin.AdminUserListResource
import com.sympauthy.api.util.COLLECTION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.SEARCH_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.collection.UserCollectionManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
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

@Controller("/api/v1/admin/users")
@Secured(ADMIN_USERS_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
class AdminUserController(
    @Inject private val userManager: UserManager,
    @Inject private val userCollectionManager: UserCollectionManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val userMapper: AdminUserResourceMapper,
    @Inject private val userDetailMapper: AdminUserDetailResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    companion object {
        /**
         * The one parameter of this collection that is not a criterion: it selects which claim values
         * come back rather than which users do.
         */
        private val SELECTION_PARAMS = setOf("claims")
    }

    @Operation(
        description = "Retrieve the users. Every enabled claim this server does not compute is also a field " +
                "of its own, named by the claim's identifier. " +
                "Users are ordered by creation date, oldest first, unless another order is asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/users/capabilities.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of users."),
            ApiResponse(
                responseCode = "400",
                description = COLLECTION_BAD_REQUEST_DESCRIPTION +
                        "\n- `collection.search.unsupported`: `q` is sent while every enabled claim is one " +
                        "this server computes, so the collection searches nothing." +
                        "\n- `user.claims.unknown`: `claims` names a claim that is not configured or not enabled."
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:read`."
            )
        ]
    )
    @Get
    suspend fun listUsers(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(
            description = "Comma-separated identifiers of the claims whose values each user carries, for " +
                    "example `email,name`. When absent, every enabled claim. When empty, none."
        ) claims: String?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the value of every enabled claim this " +
                    "server does not compute."
        ) q: String?
    ): AdminUserListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, userCollectionManager.capabilities(), sort, q, SELECTION_PARAMS)
        val selectedClaims = userCollectionManager.listSelectedClaims(claimIdsOf(claims))
        val users = userCollectionManager.listUsers(criteria, pageParams)

        return AdminUserListResource(
            users = users.items.map { userMapper.toResource(it, selectedClaims) },
            page = users.page,
            size = users.size,
            total = users.total
        )
    }

    @Operation(
        description = "Retrieve what the user collection accepts: the fields it filters on and the " +
                "operators each accepts, the fields it orders on and searches, and its own order. " +
                "Every enabled claim this server does not compute is a field of its own. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the user collection accepts."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:read`."
            )
        ]
    )
    @Get("/capabilities")
    suspend fun getUserCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(userCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Retrieve a user.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The user, with the values of its identifier claims."),
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
    @Get("/{id}")
    suspend fun getUser(
        @PathVariable @Parameter(description = "Identifier of the user.") id: UUID
    ): AdminUserDetailResource {
        val user = userManager.findByIdOrNull(id).orNotFound()
        val identifierClaims = collectedClaimManager.findIdentifierByUserId(user.id)
        return userDetailMapper.toResource(user, identifierClaims)
    }

    /**
     * Split the comma-separated `claims` parameter, keeping a caller who named none apart from one
     * who named no claim at all: the first sends nothing and the second an empty value.
     */
    private fun claimIdsOf(claims: String?): List<String>? = claims
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
}
