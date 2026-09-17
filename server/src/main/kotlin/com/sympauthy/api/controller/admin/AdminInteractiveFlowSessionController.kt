package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminInteractiveFlowSessionResourceMapper
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDetailResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.filterOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.orderOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionSearchManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_INTERACTIVE_FLOW_SESSIONS_READ
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/interactive-flow-sessions")
@Secured(ADMIN_INTERACTIVE_FLOW_SESSIONS_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.INTERACTIVE_FLOW_SESSIONS_READ])
class AdminInteractiveFlowSessionController(
    @Inject private val searchManager: InteractiveFlowSessionSearchManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val sessionMapper: AdminInteractiveFlowSessionResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve a paginated list of the interactive flow sessions this server currently holds. " +
                "This is not a history: expired sessions are collected every fifteen minutes, so the window " +
                "is the session lifetime plus up to a quarter of an hour, and an empty result means 'nothing " +
                "in flight' rather than 'nothing ever happened'. " +
                "Sessions are ordered by the date they started, oldest first, then by identifier, which stays " +
                "ascending under order=desc.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of interactive flow sessions."),
            ApiResponse(
                responseCode = "400",
                description = "Invalid page, size, client, purpose, status or sort direction."
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
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue @Parameter(
            description = "Partial case-insensitive search across the observed address, the observed user " +
                    "agent and the initiating client identifier."
        ) q: String?,
        @QueryValue @Parameter(
            description = "Filter by the identifier of the client the session was started for."
        ) client: String?,
        @QueryValue @Parameter(
            description = "Filter by the identifier of the user the session identified. A session still " +
                    "signing that account up does match — it holds the identifier from the moment the " +
                    "person is identified — but publishes no user beside it until it completes."
        ) user: UUID?,
        @QueryValue @Parameter(
            description = "Filter by the purpose that started the session."
        ) purpose: String?,
        @QueryValue @Parameter(
            description = "Filter by what became of the session: ongoing, completed, cancelled, failed or expired."
        ) status: String?,
        @QueryValue @Parameter(description = "Sort direction: asc or desc. Defaults to asc.") order: String?
    ): AdminInteractiveFlowSessionListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        // Resolved before anything is read, so a filter or a direction naming nothing is refused on its own.
        val resolvedClient = filterOf("client", client, clientManager.listClients().map(Client::id))
        val resolvedPurpose = filterOf<InteractiveFlowPurpose>("purpose", purpose)
        val resolvedStatus = filterOf<InteractiveFlowSessionStatus>("status", status)
        val resolvedOrder = orderOf("order", order)

        val sessions = searchManager.listSessions(
            query = q,
            clientId = resolvedClient,
            userId = user,
            purpose = resolvedPurpose,
            status = resolvedStatus,
            order = resolvedOrder,
            pageParams = pageParams
        )

        return AdminInteractiveFlowSessionListResource(
            sessions = sessions.items.map(sessionMapper::toResource),
            page = sessions.page,
            size = sessions.size,
            total = sessions.total
        )
    }

    @Operation(
        description = "Retrieve one interactive flow session: every purpose it carries, where each one stands, " +
                "and what the handler that owns each purpose has to say about it. " +
                "A failed session publishes the message identifiers it failed with, unrendered, rather than a " +
                "sentence in a locale that may not be the reader's. " +
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
        @PathVariable @Parameter(description = "Unique identifier of the interactive flow session.") sessionId: UUID
    ): AdminInteractiveFlowSessionDetailResource {
        return sessionMapper.toResource(searchManager.findSessionOrNull(sessionId).orNotFound())
    }
}
