package com.sympauthy.api.controller.user

import com.sympauthy.api.controller.USER_SURFACE
import com.sympauthy.api.exception.accessTokenChallengeExceptionOf
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.user.UserClaimResourceMapper
import com.sympauthy.api.resource.user.UserClaimResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.security.SecurityRule.IS_USER
import com.sympauthy.security.accessTokenScheme
import com.sympauthy.security.userAuthentication
import com.sympauthy.security.userId
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.time.LocalDateTime

@Controller("$USER_SURFACE/claims")
@Secured(IS_USER)
@SecurityRequirement(name = "user")
class UserClaimController(
    @Inject private val userManager: UserManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val claimAclManager: ClaimAclManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val claimMapper: UserClaimResourceMapper
) {

    @Operation(
        description = "Retrieve the claims of the end-user the access token was issued for that they may read " +
            "through it: those not restricted to another audience than the token's whose `claims.<id>.acl` " +
            "lets the end-user read them.",
        tags = ["user"],
        responses = [
            ApiResponse(responseCode = "200", description = "The claims the end-user may read."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = NOT_A_USER_TOKEN_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "404", description = USER_NOT_FOUND_RESPONSE_DESCRIPTION)
        ]
    )
    @Get
    suspend fun getClaims(authentication: Authentication): UserClaimResource {
        val userAuth = authentication.userAuthentication
        val user = userManager.findByIdOrNull(authentication.userId).orNotFound()
        val doors = claimAclManager.getTokenDoors(userAuth.claimAclContext())
        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByPerson(
            userId = user.id,
            audienceId = userAuth.authenticationToken.grant.audienceId,
            doors = doors
        )
        val generatedClaimValues = generatedClaimsManager.computeValuesReadableByPerson(user, doors)
        return claimMapper.toResource(claims, generatedClaimValues)
    }

    @Operation(
        description = "Update the claims of the end-user the access token was issued for. The body maps each " +
            "claim identifier to its new value, and `null` or a blank value clears the claim. The end-user may " +
            "write a claim not restricted to another audience than the token's whose `claims.<id>.acl` lets " +
            "the end-user write it with their own access token, which no claim does unless the configuration " +
            "says so. Identifier claims are never written.",
        tags = ["user"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The claims the end-user may read, after the update."
            ),
            ApiResponse(
                responseCode = "400",
                description = "- `user.identifier_claim`: the body names an identifier claim.\n" +
                    "- `user.invalid_claim`: the body names a claim that is not configured or not enabled, one " +
                    "restricted to another audience, or one the end-user may not write with any access token " +
                    "this client could obtain.\n" +
                    "- `flow.claims.invalid`: one or more values are refused. `properties` holds an entry per " +
                    "refused claim, under its identifier, with a `user.claim_value_validator` code.\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(
                responseCode = "401",
                description = UNAUTHORIZED_RESPONSE_DESCRIPTION + "\n" +
                    "- `authentication.insufficient_user_authentication`: a claim may only be written by an " +
                    "end-user who authenticated more recently. The `WWW-Authenticate` challenge names the " +
                    "`max_age` to authorize again with.\n" +
                    "- `authentication.unbound_token`: a claim may only be written with an access token bound " +
                    "to a DPoP key."
            ),
            ApiResponse(
                responseCode = "403",
                description = NOT_A_USER_TOKEN_RESPONSE_DESCRIPTION + "\n" +
                    "- `forbidden`: a claim may only be written once the end-user consented to a scope they " +
                    "have not. The `WWW-Authenticate` challenge names the scopes to authorize again with."
            ),
            ApiResponse(responseCode = "404", description = USER_NOT_FOUND_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ]
    )
    @Patch
    suspend fun updateClaims(
        authentication: Authentication,
        @Body body: Map<String, Any?>
    ): UserClaimResource {
        val userAuth = authentication.userAuthentication
        // The audience the token was issued for, which for an act-as token is the one the exchange named rather
        // than the acting client's own.
        val audienceId = userAuth.authenticationToken.grant.audienceId
        val user = userManager.findByIdOrNull(authentication.userId).orNotFound()

        // Asked before the ACL below, because no answer the ACL could give would change this one: nothing in a
        // claim write proves the person controls the new value, or that the person is the one asking.
        // A deployment whose ACL opens the person's write over an identifier is told that, rather than told the
        // person may not write it.
        val identifierClaimIds = claimManager.listIdentifierClaims().map(Claim::id).toSet()
        val identifierClaim = body.keys.firstOrNull { it in identifierClaimIds }
        if (identifierClaim != null) {
            throw recoverableBusinessExceptionOf(
                "user.identifier_claim",
                "description.user.identifier_claim",
                "claim" to identifierClaim
            )
        }

        // A claim of another audience is refused rather than ignored, the same as one that does not exist: the
        // caller named it, and a silent success would read as the value having been stored.
        val claimsById = body.keys.associateWith(claimManager::findByIdOrNull)
        val unknownClaim = claimsById.entries.firstOrNull { (_, claim) ->
            claim == null || !claim.belongsToAudience(audienceId)
        }
        if (unknownClaim != null) {
            throw invalidClaimException(unknownClaim.key)
        }

        // One instant for the doors and the challenges, so a door refusing an age is never answered with a
        // challenge computed a moment later that the authentication meets.
        val now = LocalDateTime.now()
        val context = userAuth.claimAclContext()
        val doors = claimAclManager.getTokenDoors(context, now)
        val challengesByRefusedClaim = claimsById.values.filterNotNull()
            .filterNot(doors::canBeWrittenByPerson)
            .associateWith { claimAclManager.getPersonWriteChallenges(it, context, now) }
        // A claim no token could be re-authorized into writing is refused by name, before any challenge: a client
        // re-authorizing for the others would be refused again for it.
        val unchallengedClaim = challengesByRefusedClaim.entries.firstOrNull { (_, challenges) -> challenges.isEmpty() }
        if (unchallengedClaim != null) {
            throw invalidClaimException(unchallengedClaim.key.id)
        }
        if (challengesByRefusedClaim.isNotEmpty()) {
            throw accessTokenChallengeExceptionOf(
                userAuth.authenticationToken.accessTokenScheme,
                claimAclManager.mergePersonWriteChallenges(challengesByRefusedClaim.values)
            )
        }

        // The write answers every claim the person may read after it, which is the same set the read would.
        val updates = collectedClaimUpdateMapper.toUpdates(body)
        val claims = consentAwareCollectedClaimManager.updateByPerson(user, audienceId, updates, doors)
        val generatedClaimValues = generatedClaimsManager.computeValuesReadableByPerson(user, doors)
        return claimMapper.toResource(claims, generatedClaimValues)
    }

    private fun invalidClaimException(claimId: String) = recoverableBusinessExceptionOf(
        "user.invalid_claim",
        "description.user.invalid_claim",
        "claim" to claimId
    )
}

private const val NOT_A_USER_TOKEN_RESPONSE_DESCRIPTION =
    "- `forbidden`: the access token was issued by the `client_credentials` grant, with no end-user behind it."

private const val USER_NOT_FOUND_RESPONSE_DESCRIPTION =
    "- `not_found`: the end-user the access token was issued for no longer exists."
