package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.client.ClientUserClaimResourceMapper
import com.sympauthy.api.resource.client.ClientUserClaimResource
import com.sympauthy.api.resource.client.ClientUserClaimUpdateResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.ClientUserManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.user.claim.CustomClaim
import com.sympauthy.security.SecurityRule.CLIENT_USERS_CLAIMS_READ
import com.sympauthy.security.SecurityRule.CLIENT_USERS_CLAIMS_WRITE
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/client/users/{userId}/claims")
class ClientUserClaimController(
    @Inject private val clientUserManager: ClientUserManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val claimMapper: ClientUserClaimResourceMapper
) {

    @Operation(
        description = "Retrieve claims for a user (only those the user has consented to share, plus custom claims).",
        tags = ["client"],
        parameters = [
            Parameter(
                name = "userId",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "User claims."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: users:claims:read."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Get
    @Secured(CLIENT_USERS_CLAIMS_READ)
    suspend fun getUserClaims(
        authentication: Authentication,
        @PathVariable userId: UUID
    ): ClientUserClaimResource {
        val clientAuth = authentication.clientAuthentication
        clientUserManager.findUserForClientOrNull(clientAuth.clientId, userId).orNotFound()
        val consent = consentManager.findActiveConsentOrNull(userId, clientAuth.clientId).orNotFound()
        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByScopes(userId, consent.scopes)
        return claimMapper.toResource(userId, claims)
    }

    @Operation(
        description = "Update custom claims for a user. Only claims prefixed with 'custom_' can be modified.",
        tags = ["client"],
        parameters = [
            Parameter(
                name = "userId",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Updated user claims."),
            ApiResponse(
                responseCode = "400",
                description = "Attempted to modify a non-custom claim."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: users:claims:write."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Patch
    @Secured(CLIENT_USERS_CLAIMS_WRITE)
    suspend fun updateUserClaims(
        authentication: Authentication,
        @PathVariable userId: UUID,
        @Body body: ClientUserClaimUpdateResource
    ): ClientUserClaimResource {
        val clientAuth = authentication.clientAuthentication

        // Validate all keys are custom claims
        val invalidClaim = body.claims.keys.firstOrNull { claimId ->
            val claim = claimManager.findByIdOrNull(claimId)
            claim == null || claim !is CustomClaim
        }
        if (invalidClaim != null) {
            throw recoverableBusinessExceptionOf(
                "client.invalid_claim",
                "description.client.invalid_claim",
                "claim" to invalidClaim
            )
        }

        val clientUser = clientUserManager.findUserForClientOrNull(clientAuth.clientId, userId).orNotFound()
        val consent = consentManager.findActiveConsentOrNull(userId, clientAuth.clientId).orNotFound()
        val updates = collectedClaimUpdateMapper.toUpdates(body.claims)
        consentAwareCollectedClaimManager.update(clientUser.user, updates, consent.scopes)

        // Return the full claim set after update
        val allClaims = consentAwareCollectedClaimManager.findByUserIdAndReadableByScopes(userId, consent.scopes)
        return claimMapper.toResource(userId, allClaims)
    }
}
