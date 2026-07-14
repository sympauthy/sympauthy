package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.actas.ActAsRuleManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.ACCESS_DENIED
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TARGET
import com.sympauthy.config.model.AudiencesConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.exception.LocalizedException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Handles OAuth 2.0 Token Exchange (RFC 8693) requests.
 *
 * Phase 1 — **act as a user by id**: a confidential client presents its own client-credentials access token as the
 * `subject_token` (to prove it is the actor) and names the target user via `requested_subject`. When authorized by the
 * configured `rules.act_as` rules, an identity-only access token is issued with the user as `sub` and the acting client
 * recorded in the `act` claim.
 */
@Singleton
class TokenExchangeManager(
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val accessTokenGenerator: AccessTokenGenerator,
    @Inject private val userManager: UserManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val actAsRuleManager: ActAsRuleManager,
    @Inject private val uncheckedAudiencesConfig: AudiencesConfig
) {

    /**
     * Exchange the acting client's own client-credentials [subjectToken] for an identity-only access token that acts on
     * behalf of the user identified by [requestedSubject] (Phase 1).
     *
     * @param actingClient the authenticated confidential client, which is the actor.
     * @param subjectToken the client's own client-credentials access token.
     * @param subjectTokenType must be [ACCESS_TOKEN_TYPE].
     * @param requestedSubject the id (UUID) of the user to act on behalf of.
     * @param resource RFC 8693 `resource`: a URI where the client intends to use the token, mapped by the server to a
     * policy. It never contributes to the issued token's audience. Not supported yet: rejected when present.
     * @param audience RFC 8693 `audience`: the logical name of the target service; the only input used to resolve the
     * issued token's audience.
     * @param dpopJkt the JWK thumbprint to bind the issued token to, or null for a bearer token.
     */
    suspend fun exchangeForActAsToken(
        actingClient: Client,
        subjectToken: String,
        subjectTokenType: String,
        requestedSubject: String,
        resource: String? = null,
        audience: String? = null,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        if (subjectTokenType != ACCESS_TOKEN_TYPE) {
            throw oauth2ExceptionOf(
                INVALID_REQUEST, "token_exchange.unsupported_subject_token_type", "type" to subjectTokenType
            )
        }
        // `resource` selects a policy for a target URI, which this server does not implement yet; it must never be
        // silently ignored (it would change where the client believes the token is usable), so reject it when present.
        if (!resource.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_TARGET, "token_exchange.unsupported_resource")
        }

        val actorToken = validateActorSubjectToken(actingClient, subjectToken)

        val targetUser = resolveTargetUser(requestedSubject)
        val targetClaims = collectedClaimManager.findByUserId(targetUser)

        if (!actAsRuleManager.isActAsAllowed(actingClient, targetClaims)) {
            throw oauth2ExceptionOf(ACCESS_DENIED, "token_exchange.not_allowed")
        }

        val tokenAudience = resolveTargetAudience(actingClient, audience)

        return accessTokenGenerator.generateActAsAccessToken(
            userId = targetUser,
            actorToken = actorToken,
            tokenAudience = tokenAudience,
            dpopJkt = dpopJkt
        )
    }

    /**
     * Validate that [subjectToken] is a valid, non-revoked client-credentials access token issued to [actingClient].
     */
    private suspend fun validateActorSubjectToken(
        actingClient: Client,
        subjectToken: String
    ): AuthenticationToken {
        val decodedToken = try {
            jwtManager.decodeAndVerify(ACCESS_KEY, subjectToken)
        } catch (e: LocalizedException) {
            throw oauth2ExceptionOf(INVALID_GRANT, e.detailsId)
        }

        // getAuthenticationToken throws if the token is unknown, revoked or its subject does not match.
        val token = tokenManager.getAuthenticationToken(decodedToken)
        if (token.clientId != actingClient.id) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token_exchange.subject_token_not_owned")
        }
        // An act-as token already carries an actor. Exchanging it again would chain act-as tokens, which is not
        // supported yet. Checked before the user check below because an act-as token is also bound to a user, so
        // this surfaces the specific chaining error rather than the generic "not a client token" one.
        if (token.actorTokenId != null) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token_exchange.subject_token_has_actor")
        }
        // A client-credentials token is not associated with any user.
        if (token.userId != null) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token_exchange.subject_token_not_client")
        }
        return token
    }

    /**
     * Resolve the [requestedSubject] to an existing user id.
     */
    private suspend fun resolveTargetUser(requestedSubject: String): UUID {
        val targetUserId = try {
            UUID.fromString(requestedSubject)
        } catch (e: IllegalArgumentException) {
            throw oauth2ExceptionOf(INVALID_TARGET, "token_exchange.invalid_subject")
        }
        return userManager.findByIdOrNull(targetUserId)?.id
            ?: throw oauth2ExceptionOf(INVALID_TARGET, "token_exchange.invalid_subject")
    }

    /**
     * Resolve the issued token's audience from the RFC 8693 [audience] parameter (the logical name of the target
     * service), matched against a configured audience by token audience or id. Defaults to the acting client's own
     * audience when [audience] is not provided.
     */
    private fun resolveTargetAudience(
        actingClient: Client,
        audience: String?
    ): String {
        if (audience.isNullOrBlank()) {
            return actingClient.audience.tokenAudience
        }
        val audiences = uncheckedAudiencesConfig.orThrow().audiences
        return (audiences.firstOrNull { it.tokenAudience == audience || it.id == audience }
            ?: throw oauth2ExceptionOf(INVALID_TARGET, "token_exchange.invalid_target", "target" to audience))
            .tokenAudience
    }

    companion object {
        /**
         * RFC 8693 token type identifier for an OAuth 2.0 access token.
         */
        const val ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token"
    }
}
