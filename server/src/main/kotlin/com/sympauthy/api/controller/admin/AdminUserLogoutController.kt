package com.sympauthy.api.controller.admin

import com.sympauthy.api.resource.admin.AdminForceLogoutResource
import com.sympauthy.api.util.PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.security.SecurityRule.ADMIN_CONSENT_WRITE
import com.sympauthy.security.userId
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/logout")
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONSENT_WRITE])
class AdminUserLogoutController(
    @Inject private val userManager: UserManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val grantManager: GrantManager
) {

    @Operation(
        description = "Revoke every grant of a user, and every token issued under them, signing the end-user " +
                "out of every client.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The user and the number of grants revoked."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:consent:write`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no user has this identifier."
            )
        ]
    )
    @Post
    @Secured(ADMIN_CONSENT_WRITE)
    suspend fun forceLogout(
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        authentication: Authentication
    ): AdminForceLogoutResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val grantsRevoked = grantManager.revokeGrantsByUser(
            userId = userId,
            revokedBy = GrantRevokedBy.ADMIN,
            revokedById = authentication.userId
        )
        return AdminForceLogoutResource(
            userId = userId,
            clientId = null,
            grantsRevoked = grantsRevoked
        )
    }

    @Operation(
        description = "Revoke every grant of a user for one client, and every token issued under them, signing " +
                "the end-user out of it.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The user, the client and the number of grants revoked."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:consent:write`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no user has the identifier `userId`, or no client has the " +
                        "identifier `clientId`."
            )
        ]
    )
    @Post("/{clientId}")
    @Secured(ADMIN_CONSENT_WRITE)
    suspend fun forceClientLogout(
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Identifier of the client whose grants to revoke.") clientId: String,
        authentication: Authentication
    ): AdminForceLogoutResource {
        userManager.findByIdOrNull(userId).orNotFound()
        clientManager.findClientByIdOrNull(clientId).orNotFound()
        val grantsRevoked = grantManager.revokeGrantsByUserAndClient(
            userId = userId,
            clientId = clientId,
            revokedBy = GrantRevokedBy.ADMIN,
            revokedById = authentication.userId
        )
        return AdminForceLogoutResource(
            userId = userId,
            clientId = clientId,
            grantsRevoked = grantsRevoked
        )
    }
}
