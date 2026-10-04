package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.business.model.user.publishedMembers
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Issues the access token a client presents to a resource server.
 *
 * Beside what the token says about its own authorization, it carries the claims the deployment publishes
 * in [ClaimPublicationPlace.ACCESS_TOKEN] and the client may read — so a resource server reads an attribute of
 * the person off the credential it was handed rather than calling `/userinfo` for it. That is the one
 * place a claim travels as a bearer credential presented on every request, for the life of the token, so
 * withdrawing one from a token already issued means revoking that token.
 */
@Singleton
class AccessTokenGenerator(
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: EncodedAuthenticationTokenMapper,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Generate a new access token using the information stored in the session's [oauth2] request record.
     *
     * [audience] is the one the token is issued for: it names the token's own `aud`, and a claim
     * restricted to another audience is left out.
     */
    suspend fun generateAccessToken(
        oauth2: InteractiveFlowSessionOAuth2,
        userId: UUID,
        audience: Audience,
        dpopJkt: String? = null
    ) = generateAccessToken(
        userId = userId,
        clientId = oauth2.clientId,
        audience = audience,
        grantedScopes = oauth2.grantedScopes ?: emptyList(),
        grantedAt = oauth2.grantedAt,
        grantedBy = oauth2.grantedBy?.name,
        consentedScopes = oauth2.consentedScopes ?: emptyList(),
        consentedAt = oauth2.consentedAt,
        consentedBy = oauth2.consentedBy?.name,
        clientScopes = emptyList(),
        sessionId = oauth2.sessionId,
        grantType = "authorization_code",
        authenticationDate = oauth2.authenticationDate,
        requestedClaims = oauth2.requestedClaims,
        dpopJkt = dpopJkt
    )

    /**
     * Generate a new access token using the information stored in a [refreshToken].
     *
     * [audience] is the one the token is issued for: it names the token's own `aud`, and a claim
     * restricted to another audience is left out.
     */
    suspend fun generateAccessToken(
        refreshToken: AuthenticationToken,
        audience: Audience,
        dpopJkt: String? = null
    ) = generateAccessToken(
        userId = refreshToken.userId,
        clientId = refreshToken.clientId,
        audience = audience,
        grantedScopes = refreshToken.grantedScopes,
        grantedAt = refreshToken.grantedAt,
        grantedBy = refreshToken.grantedBy?.name,
        consentedScopes = refreshToken.consentedScopes,
        consentedAt = refreshToken.consentedAt,
        consentedBy = refreshToken.consentedBy?.name,
        clientScopes = refreshToken.clientScopes,
        sessionId = refreshToken.sessionId,
        grantType = "refresh_token",
        authenticationDate = refreshToken.authenticationDate,
        requestedClaims = refreshToken.requestedClaims,
        dpopJkt = dpopJkt
    )

    /**
     * Generate an identity-only access token that acts on behalf of a user via OAuth 2.0 Token Exchange
     * (RFC 8693 delegation).
     *
     * The token carries the target [userId] as `sub`, the acting client (derived from [actorToken]) as `client_id` and
     * in the `act` claim, and no scopes. The resource server authorizes from the asserted identity and the trusted
     * actor. The [actorToken] is the client-credentials token that was exchanged; its id is recorded for provenance.
     *
     * [audience] is the one the exchange named, which is where the token goes and which claims it may
     * carry. It holds none of the person's beyond the identity: a token carrying no scope satisfies no
     * claim's ACL, so the publication rule has nothing left to place.
     *
     * It states no `auth_time`: nobody proved a credential of the target account here, and the acting client's
     * own token is not a person's authentication. A resource server reading recency off this token would read
     * the moment a backend asked to act as somebody.
     */
    suspend fun generateActAsAccessToken(
        userId: UUID,
        actorToken: AuthenticationToken,
        audience: Audience,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        return generateAccessToken(
            userId = userId,
            clientId = actorToken.clientId,
            audience = audience,
            grantedScopes = emptyList(),
            grantedAt = null,
            grantedBy = null,
            consentedScopes = emptyList(),
            consentedAt = null,
            consentedBy = null,
            clientScopes = emptyList(),
            sessionId = null,
            grantType = GrantType.TOKEN_EXCHANGE.wireName,
            dpopJkt = dpopJkt,
            actorClientId = actorToken.clientId,
            actorTokenId = actorToken.id
        )
    }

    /**
     * Generate an access token for client credentials flow (machine-to-machine).
     * This token is not associated with any end-user, and carries no claim of one.
     *
     * [audience] is the one the token is issued for, and it names the token's own `aud`.
     */
    suspend fun generateAccessTokenForClient(
        clientId: String,
        audience: Audience,
        clientScopes: List<String>,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        return generateAccessToken(
            userId = null,
            clientId = clientId,
            audience = audience,
            grantedScopes = emptyList(),
            grantedAt = null,
            grantedBy = null,
            consentedScopes = emptyList(),
            consentedAt = null,
            consentedBy = null,
            clientScopes = clientScopes,
            sessionId = null,
            grantType = "client_credentials",
            dpopJkt = dpopJkt
        )
    }

    internal suspend fun generateAccessToken(
        userId: UUID?,
        clientId: String,
        audience: Audience,
        grantedScopes: List<String>,
        grantedAt: java.time.LocalDateTime?,
        grantedBy: String?,
        consentedScopes: List<String>,
        consentedAt: java.time.LocalDateTime?,
        consentedBy: String?,
        clientScopes: List<String>,
        sessionId: UUID?,
        grantType: String,
        /**
         * When the person named by [userId] proved a credential of their account, in the flow the
         * authorization came from. A caller issuing a token no person's authentication is behind — a
         * client-credentials grant, a token exchange — passes null, and the token then states no
         * `auth_time`.
         */
        authenticationDate: LocalDateTime? = null,
        /**
         * The claims the `claims` request parameter of the authorization named, carried onto the row so
         * that `/userinfo` read with this token honours the same request the id token did. It decides
         * nothing about this token's own contents: the access token is not a channel a request can name —
         * see [RequestedClaims] and [ClaimPublicationPlace.nameableInAClaimsRequest].
         */
        requestedClaims: RequestedClaims = RequestedClaims.NONE,
        dpopJkt: String? = null,
        /**
         * When non-null, the token records this client as the actor via the RFC 8693 `act` claim
         * (`{ "sub": actorClientId }`). Set for act-as tokens issued through token exchange.
         */
        actorClientId: String? = null,
        /**
         * Id of the token that was exchanged to issue this token (RFC 8693). Stored for provenance.
         */
        actorTokenId: UUID? = null
    ): EncodedAuthenticationToken {
        val authConfig = uncheckedAuthConfig.orThrow()
        val allScopes = grantedScopes + consentedScopes + clientScopes
        val tokenAudience = audience.tokenAudience

        val publishedClaims = publishedClaimsOf(userId, audience.id, consentedScopes, clientScopes)

        val issueDate = LocalDateTime.now()
        val expirationDate = issueDate.plus(authConfig.token.accessExpiration)
        val entity = AuthenticationTokenEntity(
            userId = userId,
            type = ACCESS.name,
            clientId = clientId,
            grantedScopes = grantedScopes.toTypedArray(),
            grantedAt = grantedAt,
            grantedBy = grantedBy,
            consentedScopes = consentedScopes.toTypedArray(),
            consentedAt = consentedAt,
            consentedBy = consentedBy,
            clientScopes = clientScopes.toTypedArray(),
            sessionId = sessionId,
            grantType = grantType,
            authenticationDate = authenticationDate,
            requestedIdTokenClaims = requestedClaims.idTokenClaimIds.toTypedArray(),
            requestedUserinfoClaims = requestedClaims.userInfoClaimIds.toTypedArray(),
            dpopJkt = dpopJkt,
            actorTokenId = actorTokenId,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(
            name = JwtManager.ACCESS_KEY,
            headers = mapOf("typ" to "at+jwt")
        ) {
            entity.id?.toString()?.let(this::jwtID)
            audience(listOf(tokenAudience))
            subject(userId?.toString() ?: clientId)
            claim("client_id", clientId)
            claim("scope", allScopes.joinToString(" "))
            // RFC 9068 §2.2.1 puts it here so a resource server that is not this one can tell a password
            // typed a minute ago from one typed a month ago, which `iat` cannot say across a refresh.
            authenticationDate?.let { claim(OpenIdConnectClaimId.AUTH_TIME, it.toEpochSecond(ZoneOffset.UTC)) }
            actorClientId?.let { claim("act", mapOf("sub" to it)) }
            dpopJkt?.let { claim("cnf", mapOf("jkt" to it)) }
            issueTime(Date.from(issueDate.toInstant(ZoneOffset.UTC)))
            expirationTime(Date.from(expirationDate.toInstant(ZoneOffset.UTC)))
            // Last, and against what this token has already claimed, so that nothing a deployment named
            // after a member the token states about its own authorization displaces it.
            publishedClaims.publishedMembers(reserved = claims.keys).forEach { (name, value) ->
                claim(name, value)
            }
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }

    /**
     * The claims of the person named by [userId] this token carries: the ones a client holding
     * [consentedScopes] and [clientScopes] may read, of the audience identified by [audienceId], that the
     * deployment publishes in the access token.
     *
     * Empty for a null [userId], which is a token no person is behind and therefore one with no claims to
     * read for anybody — a `client_credentials` grant is the case.
     */
    private suspend fun publishedClaimsOf(
        userId: UUID?,
        audienceId: String,
        consentedScopes: List<String>,
        clientScopes: List<String>
    ): List<CollectedClaim> {
        if (userId == null) return emptyList()
        return consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
            userId = userId,
            audienceId = audienceId,
            place = ClaimPublicationPlace.ACCESS_TOKEN,
            consentedScopes = consentedScopes,
            clientScopes = clientScopes
        )
    }
}
