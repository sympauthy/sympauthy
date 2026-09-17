package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.client.ClientUserClaimResourceMapper
import com.sympauthy.api.resource.client.ClientUserClaimResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.ClientUserManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.security.SecurityRule.CLIENT_USERS_CLAIMS_READ
import com.sympauthy.security.SecurityRule.CLIENT_USERS_CLAIMS_WRITE
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/client/users/{userId}/claims")
class ClientUserClaimController(
    @Inject private val clientManager: ClientManager,
    @Inject private val clientUserManager: ClientUserManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val claimMapper: ClientUserClaimResourceMapper
) {

    @Operation(
        description = "Retrieve claims for a user (only those the user has consented to share, plus custom claims).",
        tags = ["client"],
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
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.USERS_CLAIMS_READ])
    suspend fun getUserClaims(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID
    ): ClientUserClaimResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientManager.findClientById(clientAuth.clientId)
        val audienceId = client.audience.id
        val clientScopeIds = clientAuth.scopes.map { it.scope }
        clientUserManager.findUserForAudienceOrNull(audienceId, userId).orNotFound()
        val consent = consentManager.findActiveConsentByAudienceOrNull(userId, audienceId).orNotFound()
        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
            userId, audienceId, consent.scopes, clientScopeIds
        )
        val generatedClaimValues = generatedClaimsManager.computeValues(userId)
        return claimMapper.toResource(userId, claims, generatedClaimValues)
    }

    @Operation(
        description = "Update custom claims for a user. Only claims prefixed with 'custom_' can be modified.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "200", description = "Updated user claims."),
            ApiResponse(
                responseCode = "400",
                description = "Attempted to modify a non-custom claim, or an identifier claim."
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
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.USERS_CLAIMS_WRITE])
    suspend fun updateUserClaims(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @Body body: Map<String, Any?>
    ): ClientUserClaimResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientManager.findClientById(clientAuth.clientId)
        val audienceId = client.audience.id
        val clientScopeIds = clientAuth.scopes.map { it.scope }

        val clientUser = clientUserManager.findUserForAudienceOrNull(audienceId, userId).orNotFound()
        val consent = consentManager.findActiveConsentByAudienceOrNull(userId, audienceId).orNotFound()

        // Asked before the ACL below, because no answer the ACL could give would change this one: an
        // identifier is what the account signs in with, nothing in a claim write verifies the value it
        // stores, and a client able to set one could move an account's sign-in to an address it holds. A
        // deployment that granted the write scope over it is told that, rather than told it lacks a scope.
        // Nothing here changes an identifier either: proving the new value and the person asking is a flow,
        // and `docs/security.md` records that the server does not serve one.
        val identifierClaimIds = claimManager.listIdentifierClaims().map(Claim::id).toSet()
        val identifierClaim = body.keys.firstOrNull { it in identifierClaimIds }
        if (identifierClaim != null) {
            throw recoverableBusinessExceptionOf(
                "client.identifier_claim",
                "description.client.identifier_claim",
                "claim" to identifierClaim
            )
        }

        // Validate all keys are claims of this audience and writable by the client. A claim of another
        // audience is refused rather than ignored, the same as one the client may not write: the caller named
        // it, and a silent success would read as the value having been stored.
        val invalidClaim = body.keys.firstOrNull { claimId ->
            val claim = claimManager.findByIdOrNull(claimId)
            claim == null ||
                    !claim.belongsToAudience(audienceId) ||
                    !claim.canBeWrittenByClient(consent.scopes, clientScopeIds)
        }
        if (invalidClaim != null) {
            throw recoverableBusinessExceptionOf(
                "client.invalid_claim",
                "description.client.invalid_claim",
                "claim" to invalidClaim
            )
        }

        val updates = collectedClaimUpdateMapper.toUpdates(body)
        consentAwareCollectedClaimManager.updateByClient(
            clientUser.user, audienceId, updates, consent.scopes, clientScopeIds
        )

        // Return the full claim set after update
        val allClaims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
            userId, audienceId, consent.scopes, clientScopeIds
        )
        val generatedClaimValues = generatedClaimsManager.computeValues(userId)
        return claimMapper.toResource(userId, allClaims, generatedClaimValues)
    }
}
