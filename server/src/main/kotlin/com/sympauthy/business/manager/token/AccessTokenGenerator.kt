package com.sympauthy.business.manager.token

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.token.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.business.model.user.publishedMembers
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
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
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val customSpanTracer: CustomSpanTracer
) {

    /**
     * Generate a new access token under the [grant], recorded as issued by [grantType]: the grant type that
     * opened the [grant], unless a refresh issues it.
     *
     * [audience] is the one the token is issued for: it names the token's own `aud`, and a claim
     * restricted to another audience is left out. [doors] are the ones this token comes through: built in
     * [ClaimAclContext.of] the [grant], the client the token is issued to — which is not always of [audience] —
     * and [dpopJkt], and a claim is carried where the client's read door of it is open.
     *
     * The token states everything about its authorization off the [grant]: the subject — the person, or the
     * client where the grant has none — the scopes, the `auth_time` of the person's authentication, and,
     * for a grant a token exchange opened, the acting client in the RFC 8693 `act` claim. A grant no person
     * proved a credential behind — a `client_credentials` grant, a token exchange — states no `auth_time`.
     * An act-as grant holds no scope, and a token carrying none satisfies no claim's ACL, so it holds none of
     * the person's claims beyond the identity.
     *
     * It expires no later than the [grant], so a resource server that is not this one stops honouring it
     * when the grant stops. Passing [dpopJkt] binds it to that JWK thumbprint, and leaving it null issues a
     * bearer token.
     */
    suspend fun generateAccessToken(
        grant: Grant,
        audience: Audience,
        doors: ClaimAclTokenDoors,
        grantType: GrantType = grant.grantType,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        return customSpanTracer.issueToken(CustomSpanTracer.ACCESS_TOKEN) {
            val authConfig = uncheckedAuthConfig.orThrow()
            val tokenAudience = audience.tokenAudience

            val publishedClaims = publishedClaimsOf(grant, audience.id, doors)

            val issueDate = LocalDateTime.now()
            val expirationDate = grant.getTokenExpirationDate(issueDate.plus(authConfig.token.accessExpiration))
            val entity = AuthenticationTokenEntity(
                type = ACCESS.name,
                grantId = grant.id,
                grantType = grantType.wireName,
                dpopJkt = dpopJkt,
                issueDate = issueDate,
                expirationDate = expirationDate
            ).let { tokenRepository.save(it) }

            val encodedToken = jwtManager.create(
                name = JwtManager.ACCESS_KEY,
                headers = mapOf("typ" to "at+jwt")
            ) {
                entity.id?.toString()?.let(this::jwtID)
                audience(listOf(tokenAudience))
                subject(grant.userId?.toString() ?: grant.clientId)
                claim("client_id", grant.clientId)
                claim("scope", grant.allScopes.joinToString(" "))
                // RFC 9068 §2.2.1 puts it here so a resource server that is not this one can tell a password
                // typed a minute ago from one typed a month ago, which `iat` cannot say across a refresh.
                grant.authenticationDate?.let {
                    claim(OpenIdConnectClaimId.AUTH_TIME, it.toEpochSecond(ZoneOffset.UTC))
                }
                if (grant.grantType == GrantType.TOKEN_EXCHANGE) {
                    claim("act", mapOf("sub" to grant.clientId))
                }
                dpopJkt?.let { claim("cnf", mapOf("jkt" to it)) }
                issueTime(Date.from(issueDate.toInstant(ZoneOffset.UTC)))
                expirationDate?.let { expirationTime(Date.from(it.toInstant(ZoneOffset.UTC))) }
                // Last, and against what this token has already claimed, so that nothing a deployment named
                // after a member the token states about its own authorization displaces it.
                publishedClaims.publishedMembers(reserved = claims.keys).forEach { (name, value) ->
                    claim(name, value)
                }
            }

            tokenMapper.toEncodedAuthenticationToken(entity, grant, encodedToken)
        }
    }

    /**
     * The claims of the person the [grant] was opened for that a token under it carries: the ones a client
     * may read through [doors], of the audience identified by [audienceId], that the deployment publishes in
     * the access token.
     *
     * Empty for a grant no person is behind, and therefore one with no claims to read for anybody — a
     * `client_credentials` grant is the case.
     */
    private suspend fun publishedClaimsOf(
        grant: Grant,
        audienceId: String,
        doors: ClaimAclTokenDoors
    ): List<CollectedClaim> {
        val userId = grant.userId ?: return emptyList()
        return consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
            userId = userId,
            audienceId = audienceId,
            place = ClaimPublicationPlace.ACCESS_TOKEN,
            doors = doors
        )
    }
}
