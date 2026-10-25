package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.client.ClientUserClaimResourceMapper
import com.sympauthy.api.resource.client.ClientUserClaimResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.ClientUserManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.security.SecurityRule.IS_CLIENT
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
@Secured(IS_CLIENT)
@SecurityRequirement(name = "client")
class ClientUserClaimController(
    @Inject private val clientUserManager: ClientUserManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val claimAclManager: ClaimAclManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val claimMapper: ClientUserClaimResourceMapper
) {

    @Operation(
        description = "Retrieve the claims of a user this client may read: those not restricted to another " +
            "audience whose `claims.<id>.acl` lets a client read them, by the end-user's consent or by a scope " +
            "this client holds.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "200", description = "The claims of the user this client may read."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "404", description = CLIENT_USER_NOT_FOUND_RESPONSE_DESCRIPTION)
        ]
    )
    @Get
    suspend fun getUserClaims(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID
    ): ClientUserClaimResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientAuth.client
        val audienceId = client.audience.id
        val clientUser = clientUserManager.findUserForAudienceOrNull(audienceId, userId).orNotFound()
        val consent = consentManager.findActiveConsentByAudienceOrNull(userId, audienceId).orNotFound()
        val context = clientAuth.claimAclContext(consent.scopes)
        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(userId, audienceId, context)
        val generatedClaimValues = generatedClaimsManager.computeValuesReadableByClient(clientUser.user, context)
        return claimMapper.toResource(userId, claims, generatedClaimValues)
    }

    @Operation(
        description = "Update the claims of a user. The body maps each claim identifier to its new value, and " +
            "`null` or a blank value clears the claim. This client may write a claim whose `claims.<id>.acl` lets " +
            "a client write it, by the end-user's consent or by a scope this client holds: one of kind " +
            "`application` not restricted to another audience, or one of kind `personal` restricted to this " +
            "client's audience. Identifier claims are never written.",
        tags = ["client"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The claims of the user this client may read, after the update."
            ),
            ApiResponse(
                responseCode = "400",
                description = "- `client.identifier_claim`: the body names an identifier claim.\n" +
                    "- `client.invalid_claim`: the body names a claim that is not configured or not enabled, " +
                    "one restricted to another audience, or one this client may not write.\n" +
                    "- `flow.claims.invalid`: one or more values are refused. `properties` holds an entry per " +
                    "refused claim, under its identifier, with a `user.claim_value_validator` code.\n" +
                    PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION + "\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "404", description = CLIENT_USER_NOT_FOUND_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ]
    )
    @Patch
    suspend fun updateUserClaims(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        @Body body: Map<String, Any?>
    ): ClientUserClaimResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientAuth.client
        val audienceId = client.audience.id

        val clientUser = clientUserManager.findUserForAudienceOrNull(audienceId, userId).orNotFound()
        val consent = consentManager.findActiveConsentByAudienceOrNull(userId, audienceId).orNotFound()

        // Asked before the ACL below, because no answer the ACL could give would change this one: an
        // identifier is what the account signs in with, nothing in a claim write verifies the value it
        // stores, and a client able to set one could move an account's sign-in to an address it holds. A
        // deployment whose ACL opens the client write over it is told that, rather than told the client may
        // not write it.
        // Nothing here changes an identifier either: proving the new value and the person asking is a flow,
        // and `docs/design/security.md` records that the server does not serve one.
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
        val context = clientAuth.claimAclContext(consent.scopes)
        val doors = claimAclManager.getTokenDoors(context)
        val invalidClaim = body.keys.firstOrNull { claimId ->
            val claim = claimManager.findByIdOrNull(claimId)
            claim == null ||
                    !claim.belongsToAudience(audienceId) ||
                    !doors.canBeWrittenByClient(claim)
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
            clientUser.user, client.audience, updates, context
        )

        // Return the full claim set after update
        val allClaims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
            userId, audienceId, context
        )
        val generatedClaimValues = generatedClaimsManager.computeValuesReadableByClient(clientUser.user, context)
        return claimMapper.toResource(userId, allClaims, generatedClaimValues)
    }
}
