package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminUserClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminUserClaimListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
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
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val userClaimMapper: AdminUserClaimResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve a paginated list of claims for a given user, with metadata and filtering.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of user claims."),
            ApiResponse(responseCode = "400", description = "Invalid page or size."),
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
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(description = "Number of results per page.") size: Int?,
        @QueryValue("claim_id") @Parameter(description = "Filter by specific claim identifier.") claimId: String?,
        @QueryValue @Parameter(description = "Filter by whether the claim is an identifier claim.") identifier: Boolean?,
        @QueryValue @Parameter(description = "Filter by whether the claim is required.") required: Boolean?,
        @QueryValue @Parameter(description = "Filter by whether the claim has been collected.") collected: Boolean?,
        @QueryValue @Parameter(description = "Filter by whether the claim has been verified.") verified: Boolean?,
        @QueryValue @Parameter(description = "Filter by claim origin.") origin: String?
    ): AdminUserClaimListResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val (resolvedPage, resolvedSize) = paginationUtil.resolvePageParams(page, size)

        val identifierClaimIds = uncheckedAuthConfig.orThrow()
            .identifierClaims
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

        val generatedClaimValues = generatedClaimsManager.computeValues(userId)

        val total = filteredClaims.size
        val paged = filteredClaims
            .drop(resolvedPage * resolvedSize)
            .take(resolvedSize)
            .map { claim ->
                val identifier = claim.id in identifierClaimIds
                if (claim.generated) {
                    userClaimMapper.toResourceFromGeneratedClaim(
                        claim = claim,
                        identifier = identifier,
                        generatedClaimValue = generatedClaimValues[claim.id]
                    )
                } else {
                    userClaimMapper.toResourceFromCollectedClaim(
                        claim = claim,
                        collectedClaim = collectedClaimMap[claim.id],
                        identifier = identifier
                    )
                }
            }

        return AdminUserClaimListResource(
            claims = paged,
            page = resolvedPage,
            size = resolvedSize,
            total = total
        )
    }
}
