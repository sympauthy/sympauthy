package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminConsentResourceMapper
import com.sympauthy.api.resource.admin.AdminConsentListResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.security.SecurityRule.ADMIN_CONSENT_READ
import com.sympauthy.security.SecurityRule.ADMIN_CONSENT_WRITE
import com.sympauthy.security.userId
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/consents")
class AdminConsentController(
    @Inject private val userManager: UserManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val consentMapper: AdminConsentResourceMapper
) {

    @Operation(
        description = "Retrieve a paginated list of active consents for a given user.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "userId",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            ),
            Parameter(
                name = "page",
                description = "Zero-indexed page number.",
                schema = Schema(type = "integer", defaultValue = "0")
            ),
            Parameter(
                name = "size",
                description = "Number of results per page.",
                schema = Schema(type = "integer", defaultValue = "20")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of consents."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:consent:read."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Get
    @Secured(ADMIN_CONSENT_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONSENT_READ])
    suspend fun listConsents(
        @PathVariable userId: UUID,
        @QueryValue page: Int?,
        @QueryValue size: Int?
    ): AdminConsentListResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val (page, size) = resolvePageParams(page, size)
        val allConsents = consentManager.findActiveConsentsByUser(userId)
        val paged = allConsents
            .drop(page * size)
            .take(size)
            .map(consentMapper::toResource)
        return AdminConsentListResource(
            consents = paged,
            page = page,
            size = size,
            total = allConsents.size
        )
    }

    @Operation(
        description = "Revoke the active consent for a given user and client. " +
                "This also revokes all refresh tokens issued for this user+client pair.",
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
            ApiResponse(responseCode = "204", description = "Consent revoked successfully."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:consent:write."
            ),
            ApiResponse(responseCode = "404", description = "No active consent found for this user and client.")
        ]
    )
    @Delete("/{clientId}")
    @Secured(ADMIN_CONSENT_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONSENT_WRITE])
    @Status(HttpStatus.NO_CONTENT)
    suspend fun revokeConsent(
        @PathVariable userId: UUID,
        @PathVariable clientId: String,
        authentication: Authentication
    ) {
        val consent = consentManager.findActiveConsentOrNull(userId, clientId).orNotFound()
        consentManager.revokeConsent(
            consent = consent,
            revokedBy = ConsentRevokedBy.ADMIN,
            revokedById = authentication.userId
        )
    }
}
