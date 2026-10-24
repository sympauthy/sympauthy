package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.BuiltInGrantableScopeId
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.business.model.user.publishedMembers
import com.sympauthy.config.model.AdvancedConfig
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

@Singleton
class IdTokenGenerator(
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: EncodedAuthenticationTokenMapper,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Generate a new id token under the [grant], recorded as issued by [grantType] — the grant type that opened
     * the [grant], unless a refresh issues it — containing user info accessible according to the scopes the
     * [grant] holds. Only claims the end-user has consented to share with the client are included, and of
     * those only the ones the deployment publishes in the id token — always, or on request where the `claims`
     * parameter of the authorization behind the [grant] named them.
     *
     * [audience] is the audience the client belongs to, and a claim restricted to another one is left out.
     * It is not the token's own `aud`, which OpenID Connect fixes at the client id.
     *
     * [client] is the one the token is issued to, which a claim's ACL may ask about.
     *
     * [accessToken] is the one issued in the same response, and the token's `at_hash` claim names it. It is the
     * token a claim's ACL is asked about, bound to the key whose thumbprint is [dpopJkt] or a bearer token
     * where that is null: an id token is bound to nothing itself, and is told only what the access token issued
     * beside it may be told.
     *
     * The subject, the authentication time and the claims the request asked for are the [grant]'s, and only
     * the issue date, the expiry and the claim values are read again, which is what OpenID Connect Core §12.2
     * requires of an id token issued for a refresh. Reading the authentication time again would be this
     * server saying the person authenticated at the moment their client asked for a new token, and there is
     * no request at a refresh to read the asked-for claims out of — a token that quietly lost one on its first
     * renewal would change shape on a schedule nobody chose.
     *
     * [nonce] is the one the authorization request carried, and a caller issuing for anything but that
     * request's code exchange passes null. §12.2 asks it of a refresh in as many words: a refreshed id token
     * "SHOULD NOT have a `nonce` Claim, even when the ID Token issued at the time of the original
     * authentication contained `nonce`". A nonce binds an id token to an authorization request, and no
     * authorization request was made there.
     *
     * Returns null unless the [grant] names a user and carries `openid`: a `client_credentials` grant has no
     * identity to assert, and one that never asked for OpenID Connect asked for none.
     */
    suspend fun generateIdToken(
        grant: Grant,
        client: Client,
        audience: Audience,
        accessToken: EncodedAuthenticationToken,
        dpopJkt: String?,
        grantType: GrantType = grant.grantType,
        nonce: String? = null
    ): EncodedAuthenticationToken? {
        // ID tokens are only for user authentication, not client credentials
        val userId = grant.userId ?: return null
        // An id token answers a request for `openid`, and a grant that never asked for OpenID Connect is
        // owed none. Every caller passes through here, so the authorization code and the refresh cannot
        // answer the same grant differently.
        if (!grant.grantedScopes.contains(BuiltInGrantableScopeId.OPENID)) {
            return null
        }

        val authConfig = uncheckedAuthConfig.orThrow()
        val advancedConfig = uncheckedAdvancedConfig.orThrow()

        val publishedClaims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
            userId = userId,
            audienceId = audience.id,
            place = ClaimPublicationPlace.ID_TOKEN,
            context = ClaimAclContext.of(grant, client, dpopJkt),
            requestedClaims = grant.requestedClaims
        )

        val issueDate = LocalDateTime.now()
        val expirationDate = grant.getTokenExpirationDate(issueDate.plus(authConfig.token.idExpiration))
        val entity = AuthenticationTokenEntity(
            type = AuthenticationTokenType.ID.name,
            grantId = grant.id,
            grantType = grantType.wireName,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(JwtManager.PUBLIC_KEY) {
            entity.id?.toString()?.let(this::jwtID)
            // Pretty weird but in OpenID spec, the audience is the client_id of the client which defer from OAuth2
            // spec.
            // https://openid.net/specs/openid-connect-basic-1_0.html#IDToken
            audience(listOf(grant.clientId))
            subject(generatedClaimsManager.computeSubject(userId))
            issueTime(Date.from(issueDate.toInstant(ZoneOffset.UTC)))
            expirationDate?.let { expirationTime(Date.from(it.toInstant(ZoneOffset.UTC))) }
            nonce?.let { claim("nonce", it) }
            // Claimed on every id token rather than only where a client asked with `max_age`, which is the
            // minimum OpenID Connect Core §2 allows: a resource server checking recency needs it in every
            // token, and nothing about a person is disclosed by it.
            grant.authenticationDate?.let {
                claim(OpenIdConnectClaimId.AUTH_TIME, it.toEpochSecond(ZoneOffset.UTC))
            }
            claim("at_hash", advancedConfig.publicJwtAlgorithm.hashAlgorithm.atHash(accessToken.token))
            // Last, and against what this token has already claimed, so that nothing a deployment named
            // after a member the token states about its own authorization displaces it.
            publishedClaims.publishedMembers(reserved = claims.keys).forEach { (name, value) ->
                claim(name, value)
            }
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, grant, encodedToken)
    }
}
