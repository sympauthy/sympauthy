package com.sympauthy.api.controller.admin

import com.sympauthy.api.resource.admin.AdminForceLogoutResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.security.SecurityRule.ADMIN_SESSIONS_WRITE
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/logout")
class AdminUserLogoutController(
    @Inject private val userManager: UserManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val tokenManager: TokenManager
) {

    @Operation(
        description = "Force logout a user by revoking all their tokens across all clients.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "userId",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Tokens revoked successfully."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:sessions:write."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Post
    @Secured(ADMIN_SESSIONS_WRITE)
    suspend fun forceLogout(
        @PathVariable userId: UUID
    ): AdminForceLogoutResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val tokensRevoked = tokenManager.revokeTokensByUser(userId)
        return AdminForceLogoutResource(
            userId = userId,
            clientId = null,
            tokensRevoked = tokensRevoked
        )
    }

    @Operation(
        description = "Force logout a user from a specific client by revoking their tokens for that client.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "userId",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            ),
            Parameter(
                name = "clientId",
                description = "Identifier of the client.",
                schema = Schema(type = "string")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Tokens revoked successfully."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:sessions:write."
            ),
            ApiResponse(responseCode = "404", description = "No user or client found with the given identifier.")
        ]
    )
    @Post("/{clientId}")
    @Secured(ADMIN_SESSIONS_WRITE)
    suspend fun forceClientLogout(
        @PathVariable userId: UUID,
        @PathVariable clientId: String
    ): AdminForceLogoutResource {
        userManager.findByIdOrNull(userId).orNotFound()
        clientManager.findClientByIdOrNull(clientId).orNotFound()
        val tokensRevoked = tokenManager.revokeTokensByUserAndClient(userId, clientId)
        return AdminForceLogoutResource(
            userId = userId,
            clientId = clientId,
            tokensRevoked = tokensRevoked
        )
    }
}
