package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminInteractiveFlowSessionResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDetailResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionListResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSecurityContextListResource
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.SEARCH_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
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
        description = "Retrieve a paginated list of the interactive flow sessions this server currently holds. " +
                "This is not a history: expired sessions are collected every fifteen minutes, so the window " +
                "is the session lifetime plus up to a quarter of an hour, and an empty result means 'nothing " +
                "in flight' rather than 'nothing ever happened'. " +
                "Sessions are ordered by the date they started, oldest first, then by identifier, unless " +
                "another order is asked for; that identifier stays ascending whichever direction is asked for. " +
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
            ApiResponse(
                responseCode = "400",
                description = "Invalid page or size, an unknown field, an operator the field does not " +
                        "accept, or a value it does not hold."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: " +
                        "admin:interactive-flow-sessions:read."
            )
        ]
    )
    @Get
    suspend fun listInteractiveFlowSessions(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the address and user agent of " +
                    "every place the session was driven from, and the initiating client identifier."
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
        description = "Retrieve what the interactive flow session collection accepts: the fields it filters " +
                "on and the operators each admits, the fields it orders on, the fields a free-text search " +
                "matches against, and the order it takes when none is asked for. " +
                "The names it carries are read in the language the request asked for and may be reworded " +
                "in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection accepts."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: " +
                        "admin:interactive-flow-sessions:read."
            )
        ]
    )
    @Get("/capabilities")
    suspend fun getInteractiveFlowSessionCapabilities(
        request: HttpRequest<*>
    ): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(sessionCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Retrieve one interactive flow session: every purpose it carries, where each one stands, " +
                "and what the handler that owns each purpose has to say about it. " +
                "A session that ended in a failure — one it failed with, or the expiry that ended it — " +
                "publishes the message identifiers and those messages read in the language the request " +
                "asked for: the identifiers are what a caller branches on or searches for, the sentences " +
                "are what a person reads. " +
                "The end-user's message is the one the flow's error page showed them, so a failure naming " +
                "none of its own carries the generic sentence and the identifier naming it, rather than " +
                "nothing. " +
                "The debug entries are labels written for a person: a label may be reworded in any release, " +
                "so nothing may branch on one.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The interactive flow session."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: " +
                        "admin:interactive-flow-sessions:read."
            ),
            ApiResponse(
                responseCode = "404",
                description = "No interactive flow session found with the given identifier, or it has " +
                        "already been collected."
            )
        ]
    )
    @Get("/{sessionId}")
    suspend fun getInteractiveFlowSession(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the interactive flow session.") sessionId: UUID
    ): AdminInteractiveFlowSessionDetailResource {
        return sessionMapper.toResource(
            sessionCollectionManager.findSessionOrNull(sessionId).orNotFound(),
            request.locale.orDefault()
        )
    }

    @Operation(
        description = "Retrieve a paginated list of the places one interactive flow session was driven " +
                "from: one entry per distinct address and user agent, counting the requests that came " +
                "from it rather than repeating them. " +
                "The list is bounded — once a session holds as many places as it may, a request from a " +
                "new one rolls out the place seen least recently, and a place a credential was proven " +
                "at is the last to go, so a place a reader saw earlier may be gone and a place rolled " +
                "out and seen again returns counting from one. " +
                "Entries are ordered by the last sighting, most recent first, then by address and user " +
                "agent. That first key is rewritten by every request the session makes, so two calls " +
                "agree on a snapshot while a walk in progress may see an entry twice or skip one.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of places."),
            ApiResponse(responseCode = "400", description = "Invalid page or size."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: " +
                        "admin:interactive-flow-sessions:read."
            ),
            ApiResponse(
                responseCode = "404",
                description = "No interactive flow session found with the given identifier, or it has " +
                        "already been collected."
            )
        ]
    )
    @Get("/{sessionId}/security-contexts")
    suspend fun listInteractiveFlowSessionSecurityContexts(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the interactive flow session.") sessionId: UUID,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(description = SEARCH_PARAMETER_DESCRIPTION) q: String?
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
        description = "Retrieve what the collection of the places one session was driven from accepts: the " +
                "fields it filters on and the operators each admits, the fields it orders on, the fields " +
                "a free-text search matches against, and the order it takes when none is asked for. " +
                "It describes the collection rather than one session, so it reads the same for every session " +
                "identifier and looks none of them up. " +
                "The names it carries are read in the language the request asked for and may be reworded " +
                "in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection accepts."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: " +
                        "admin:interactive-flow-sessions:read."
            )
        ]
    )
    @Get("/{sessionId}/security-contexts/capabilities")
    suspend fun getSecurityContextCapabilities(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the interactive flow session.") sessionId: UUID
    ): AdminCollectionCapabilitiesResource = capabilitiesMapper.toResource(
        sessionCollectionManager.securityContextCapabilities(),
        request.locale.orDefault()
    )
}
