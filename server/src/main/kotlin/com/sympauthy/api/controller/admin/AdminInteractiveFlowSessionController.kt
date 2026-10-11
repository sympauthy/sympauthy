package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminInteractiveFlowSessionResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDetailResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionListResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSecurityContextListResource
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
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_INTERACTIVE_FLOW_SESSIONS_READ
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

private const val FORBIDDEN_DESCRIPTION =
    "- `forbidden`: the access token does not carry the scope `admin:interactive-flow-sessions:read`."

private const val SESSION_NOT_FOUND_DESCRIPTION =
    "- `not_found`: no interactive flow session has this identifier, or it expired and was deleted."

@Controller("/api/v1/admin/interactive-flow-sessions")
@Secured(ADMIN_INTERACTIVE_FLOW_SESSIONS_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.INTERACTIVE_FLOW_SESSIONS_READ])
class AdminInteractiveFlowSessionController(
    @Inject private val sessionCollectionManager: InteractiveFlowSessionCollectionManager,
    @Inject private val sessionMapper: AdminInteractiveFlowSessionResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve the interactive flow sessions this server holds. A session is deleted in the " +
                "fifteen minutes or so after its `expiration_date`, so this is not a history. " +
                "Interactive flow sessions are ordered by the date they started, oldest first, unless another " +
                "order is asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/interactive-flow-sessions/capabilities.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of interactive flow sessions."),
            ApiResponse(responseCode = "400", description = COLLECTION_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION)
        ]
    )
    @Get
    suspend fun listInteractiveFlowSessions(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the identifier of the client, and the " +
                    "address and user agent of every security context of the interactive flow session."
        ) q: String?
    ): AdminInteractiveFlowSessionListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, sessionCollectionManager.capabilities(), sort, q)
        val sessions = sessionCollectionManager.listSessions(criteria, pageParams)

        return AdminInteractiveFlowSessionListResource(
            sessions = sessions.items.map(sessionMapper::toResource),
            page = sessions.page,
            size = sessions.size,
            total = sessions.total
        )
    }

    @Operation(
        description = "Retrieve what the interactive flow session collection accepts: the fields it filters on " +
                "and the operators each accepts, the fields it orders on and searches, and its own order. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the interactive flow session collection accepts."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION)
        ]
    )
    @Get("/capabilities")
    suspend fun getInteractiveFlowSessionCapabilities(
        request: HttpRequest<*>
    ): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(sessionCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Retrieve an interactive flow session, with each of its purposes and how far the " +
                "session got with it. Its security contexts are listed at " +
                "/api/v1/admin/interactive-flow-sessions/{sessionId}/security-contexts.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The interactive flow session."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION),
            ApiResponse(responseCode = "404", description = SESSION_NOT_FOUND_DESCRIPTION)
        ]
    )
    @Get("/{sessionId}")
    suspend fun getInteractiveFlowSession(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Identifier of the interactive flow session.") sessionId: UUID
    ): AdminInteractiveFlowSessionDetailResource {
        return sessionMapper.toResource(
            sessionCollectionManager.findSessionOrNull(sessionId).orNotFound(),
            request.locale.orDefault()
        )
    }

    @Operation(
        description = "Retrieve the security contexts an interactive flow session was used from. A session " +
                "keeps at most ten, and none once it completes. " +
                "Security contexts are ordered by their latest request, most recent first, unless another " +
                "order is asked for. New requests reorder them, so a walk through the pages may skip or " +
                "repeat one. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/interactive-flow-sessions/{sessionId}/security-contexts/capabilities.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of security contexts."),
            ApiResponse(responseCode = "400", description = COLLECTION_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION),
            ApiResponse(responseCode = "404", description = SESSION_NOT_FOUND_DESCRIPTION)
        ]
    )
    @Get("/{sessionId}/security-contexts")
    suspend fun listInteractiveFlowSessionSecurityContexts(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Identifier of the interactive flow session.") sessionId: UUID,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the address, the user agent, the " +
                    "country code, the region and the city."
        ) q: String?
    ): AdminInteractiveFlowSessionSecurityContextListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, sessionCollectionManager.securityContextCapabilities(), sort, q)
        val places = sessionCollectionManager.listSecurityContexts(sessionId, criteria, pageParams).orNotFound()
        return AdminInteractiveFlowSessionSecurityContextListResource(
            securityContexts = places.items.map(sessionMapper::toResource),
            page = places.page,
            size = places.size,
            total = places.total
        )
    }

    @Operation(
        description = "Retrieve what the collection of an interactive flow session's security contexts " +
                "accepts: the fields it filters on and the operators each accepts, the fields it orders on and " +
                "searches, and its own order. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "What the collection of an interactive flow session's security contexts accepts."
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION)
        ]
    )
    @Get("/{sessionId}/security-contexts/capabilities")
    suspend fun getSecurityContextCapabilities(
        request: HttpRequest<*>,
        @PathVariable @Parameter(
            description = "Identifier of an interactive flow session. It is not looked up: the answer is the same " +
                    "for every interactive flow session."
        ) sessionId: UUID
    ): AdminCollectionCapabilitiesResource = capabilitiesMapper.toResource(
        sessionCollectionManager.securityContextCapabilities(),
        request.locale.orDefault()
    )
}
