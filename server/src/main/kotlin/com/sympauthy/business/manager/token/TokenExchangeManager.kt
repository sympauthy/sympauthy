package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.withRefusalAs
import com.sympauthy.business.manager.actas.ActAsRuleManager
import com.sympauthy.business.manager.claim.ClaimAclManager
import com.sympauthy.business.manager.claim.CollectedClaimManager
import com.sympauthy.business.manager.grant.GrantManager
import com.sympauthy.business.manager.grant.toOAuth2Exception
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.grant.GrantOpenedForResult
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TARGET
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.user.claim.ClaimAclContext
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Handles OAuth 2.0 Token Exchange (RFC 8693) requests: a confidential client acting as a person, named by
 * id in `requested_subject`, by presenting its own client-credentials access token as the `subject_token`.
 * What an act-as token is, and what may be exchanged for one, is `docs/design/security.md`'s.
 */
@Singleton
open class TokenExchangeManager(
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val grantManager: GrantManager,
    @Inject private val accessTokenGenerator: AccessTokenGenerator,
    @Inject private val claimAclManager: ClaimAclManager,
    @Inject private val userManager: UserManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val actAsRuleManager: ActAsRuleManager
) {

    /**
     * Exchange the acting client's own client-credentials access token [request] carries as its subject token
     * for an identity-only access token that acts on behalf of the user whose id it names as the requested
     * subject (Phase 1). [actingClient] is the authenticated confidential client presenting that token and is
     * the actor the issued token records.
     *
     * The token is issued for the audience [actingClient] belongs to, and a [request] naming another audience is
     * refused. Passing [dpopJkt] binds the issued token to that JWK thumbprint, and leaving it null issues a
     * bearer token.
     *
     * The token is issued under a grant the exchange opens, which records the grant of the subject token:
     * revoking that grant revokes this one, and a token of this one is refused once that grant is no longer
     * live. It expires no later than the subject token does.
     *
     * The act-as grant and its token are written together. Committed alone, the grant is one no token and no
     * code names yet, which the collecting job may delete as finished before the token is written under it, and
     * a failure writing the token would leave a grant nothing was ever issued under.
     */
    @Transactional
    open suspend fun exchangeForActAsToken(
        actingClient: Client,
        request: TokenRequest.TokenExchange,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        val actorToken = validateActorSubjectToken(actingClient, request.subjectToken)

        val targetUser = resolveTargetUser(request.requestedSubject)
        // Checked before the rule runs, so that an exchange the audience refuses reads nothing about the user.
        val targetAudience = resolveTargetAudience(actingClient, request.audience)
        val targetClaims = collectedClaimManager.findByUserIdAndAudience(targetUser, targetAudience.id)

        // RFC 8693 §2.2.2 answers a subject unacceptable by policy with `invalid_request`.
        if (!actAsRuleManager.isActAsAllowed(actingClient, targetClaims)) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "token_exchange.not_allowed")
        }

        val grant = grantManager.createTokenExchangeGrant(
            userId = targetUser,
            audienceId = targetAudience.id,
            actorToken = actorToken
        )
        return accessTokenGenerator.generateAccessToken(
            grant = grant,
            audience = targetAudience,
            doors = claimAclManager.getTokenDoors(ClaimAclContext.of(grant, actingClient, dpopJkt)),
            dpopJkt = dpopJkt
        )
    }

    /**
     * Validate that [subjectToken] is a valid, non-revoked client-credentials access token issued to [actingClient],
     * for the audience it belongs to now.
     */
    private suspend fun validateActorSubjectToken(
        actingClient: Client,
        subjectToken: String
    ): AuthenticationToken {
        val decodedToken = withRefusalAs(INVALID_GRANT) {
            jwtManager.decodeAndVerify(ACCESS_KEY, subjectToken)
        }

        // getAuthenticationToken throws if the token is unknown, its grant revoked or its subject does not match.
        val token = tokenManager.getAuthenticationToken(decodedToken)
        when (val openedFor = grantManager.verifyOpenedFor(token.grant, actingClient)) {
            is GrantOpenedForResult.OpenedForAnotherClient ->
                throw oauth2ExceptionOf(INVALID_GRANT, "token_exchange.subject_token_not_owned")
            is GrantOpenedForResult.OpenedForAnotherAudience -> throw openedFor.toOAuth2Exception()
            GrantOpenedForResult.Opened -> Unit
        }
        // An act-as token already carries an actor, and is never exchanged for the next one. Checked before the
        // user check below because an act-as token is also bound to a user, so this surfaces the specific
        // chaining error rather than the generic "not a client token" one.
        if (token.grant.actorGrantId != null) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token_exchange.subject_token_has_actor")
        }
        // A client-credentials token is not associated with any user.
        if (token.grant.userId != null) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token_exchange.subject_token_not_client")
        }
        return token
    }

    /**
     * Resolve the [requestedSubject] to an existing user id.
     *
     * Refuses an account an interactive flow session is still signing up, as everything minting a token
     * must — but through the committed-only reader rather than `UserManager.checkPromoted`, because this
     * surface owes its caller RFC 8693's `invalid_target` and not a business failure. An unfinished account
     * is, to a token exchange, a subject naming nothing.
     */
    private suspend fun resolveTargetUser(requestedSubject: String): UUID {
        val targetUserId = try {
            UUID.fromString(requestedSubject)
        } catch (_: IllegalArgumentException) {
            throw oauth2ExceptionOf(INVALID_TARGET, "token_exchange.invalid_subject")
        }
        return userManager.findByIdOrNull(targetUserId)?.id
            ?: throw oauth2ExceptionOf(INVALID_TARGET, "token_exchange.invalid_subject")
    }

    /**
     * The audience the token is issued for, which is the one [actingClient] belongs to. Throws an
     * `OAuth2Exception` carrying `invalid_target` and `token_exchange.invalid_target` where the RFC 8693
     * [audience] parameter names anything else, matched against that audience's token audience and identifier.
     * Why no other audience is allowed is `docs/design/security.md`'s.
     */
    private fun resolveTargetAudience(
        actingClient: Client,
        audience: String?
    ): Audience {
        val clientAudience = actingClient.audience
        if (audience.isNullOrBlank() || audience == clientAudience.tokenAudience || audience == clientAudience.id) {
            return clientAudience
        }
        throw oauth2ExceptionOf(
            INVALID_TARGET,
            "token_exchange.invalid_target",
            "target" to audience,
            "audience" to clientAudience.id,
            "clientId" to actingClient.id
        )
    }
}
