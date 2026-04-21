package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminUserClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminUserClaimListResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager

import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/claims")
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
class AdminUserClaimController(
    @Inject private val userManager: UserManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val userClaimMapper: AdminUserClaimResourceMapper
) {

    @Operation(
        description = "Retrieve a paginated list of claims for a given user, with metadata and filtering.",
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
            ),
            Parameter(
                name = "claim_id",
                description = "Filter by specific claim identifier.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "identifier",
                description = "Filter by whether the claim is an identifier claim.",
                schema = Schema(type = "boolean")
            ),
            Parameter(
                name = "required",
                description = "Filter by whether the claim is required.",
                schema = Schema(type = "boolean")
            ),
            Parameter(
                name = "collected",
                description = "Filter by whether the claim has been collected.",
                schema = Schema(type = "boolean")
            ),
            Parameter(
                name = "verified",
                description = "Filter by whether the claim has been verified.",
                schema = Schema(type = "boolean")
            ),
            Parameter(
                name = "origin",
                description = "Filter by claim origin.",
                schema = Schema(type = "string", allowableValues = ["openid", "custom"])
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of user claims."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:read."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Get
    @Secured(ADMIN_USERS_READ)
    suspend fun listUserClaims(
        @PathVariable userId: UUID,
        @QueryValue page: Int?,
        @QueryValue size: Int?,
        @QueryValue("claim_id") claimId: String?,
        @QueryValue identifier: Boolean?,
        @QueryValue required: Boolean?,
        @QueryValue collected: Boolean?,
        @QueryValue verified: Boolean?,
        @QueryValue origin: String?
    ): AdminUserClaimListResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val (resolvedPage, resolvedSize) = resolvePageParams(page, size)

        val identifierClaimIds = uncheckedAuthConfig.orThrow()
            .identifierClaims
            .map { it.id }
            .toSet()

        // Get all enabled claims and filter out *_verified claims
        val allVerifiedIds = claimManager.listEnabledClaims()
            .mapNotNull { it.verifiedId }
            .toSet()
        var filteredClaims = claimManager.listEnabledClaims()
            .filter { it.id !in allVerifiedIds }

        // Apply claim-metadata-only filters
        if (claimId != null) {
            filteredClaims = filteredClaims.filter { it.id == claimId }
        }
        if (identifier != null) {
            filteredClaims = filteredClaims.filter { (it.id in identifierClaimIds) == identifier }
        }
        if (required != null) {
            filteredClaims = filteredClaims.filter { it.required == required }
        }
        if (origin != null) {
            filteredClaims = filteredClaims.filter { it.origin.value == origin.lowercase() }
        }

        // Fetch collected claims only for the filtered set
        val collectedClaimMap = collectedClaimManager.findByUserIdAndClaims(userId, filteredClaims)
            .associateBy { it.claim.id }

        // Apply collected-data-dependent filters
        if (collected != null) {
            filteredClaims = filteredClaims.filter { claim ->
                val hasValue = collectedClaimMap[claim.id]?.value != null
                hasValue == collected
            }
        }
        if (verified != null) {
            filteredClaims = filteredClaims.filter { claim ->
                val isVerified = collectedClaimMap[claim.id]?.verificationDate != null
                isVerified == verified
            }
        }

        val total = filteredClaims.size
        val paged = filteredClaims
            .drop(resolvedPage * resolvedSize)
            .take(resolvedSize)
            .map { claim ->
                userClaimMapper.toResource(
                    claim = claim,
                    collectedClaim = collectedClaimMap[claim.id],
                    identifier = claim.id in identifierClaimIds
                )
            }

        return AdminUserClaimListResource(
            claims = paged,
            page = resolvedPage,
            size = resolvedSize,
            total = total
        )
    }
}
