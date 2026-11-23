package com.sympauthy.business.manager.token

import com.sympauthy.business.manager.claim.ConsentAwareCollectedClaimManager
import com.sympauthy.business.manager.claim.GeneratedClaimsManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.claim.ClaimAclTokenDoors
import com.sympauthy.business.model.claim.ClaimPublicationPlace
import com.sympauthy.business.model.claim.OpenIdConnectClaimId
import com.sympauthy.business.model.claim.publishedMembers
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.scope.BuiltInGrantableScopeId
import com.sympauthy.business.model.token.AuthenticationTokenType
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.platform.monitoring.telemetry.manager.CustomSpanTracer
import com.sympauthy.platform.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Deferred
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
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val customSpanTracer: CustomSpanTracer
) {

    /**
     * Generate a new id token under the [grant], recorded as issued by [grantType] — the grant type that opened
     * the [grant], unless a refresh issues it. It carries the claims the client's read door of them opens
     * through [doors], and of those only the ones the deployment publishes in the id token — always, or on
     * request where the `claims` parameter of the authorization behind the [grant] named them.
     *
     * [audience] is the audience the client belongs to, and a claim restricted to another one is left out.
     * It is not the token's own `aud`, which OpenID Connect fixes at the client id.
     *
     * [accessToken] is the one issued in the same response, and the token's `at_hash` claim names it. It is
     * awaited only for that claim: the claims are read, the row written and the signing key loaded while it is
     * still being issued. [doors] are the ones that access token comes through, the same ones it was issued
     * through: an id token is bound to nothing itself, and is told only what the access token issued beside it
     * may be told.
     *
     * The subject, the authentication time and the claims the request asked for are the [grant]'s, and only
     * the issue date, the expiry and the claim values are read again, which is what OpenID Connect Core §12.2
     * requires of an id token issued for a refresh. Why a refresh reissues it so is `docs/design/security.md`'s.
     *
     * [nonce] is the one the authorization request carried, and a caller issuing for anything but that
     * request's code exchange passes null. §12.2 asks it of a refresh in as many words: a refreshed id token
     * "SHOULD NOT have a `nonce` Claim, even when the ID Token issued at the time of the original
     * authentication contained `nonce`". A nonce binds an id token to an authorization request, and no
     * authorization request was made there.
     *
     * Returns null unless the [grant] names a user and carries `openid`, as `docs/design/security.md` has it.
     */
    suspend fun generateIdToken(
        grant: Grant,
        audience: Audience,
        accessToken: Deferred<EncodedAuthenticationToken>,
        doors: ClaimAclTokenDoors,
        grantType: GrantType = grant.grantType,
        nonce: String? = null
    ): EncodedAuthenticationToken? {
        // ID tokens are only for user authentication, not client credentials
        val userId = grant.userId ?: return null
        // Checked here, which every caller passes through, so that the authorization code and the refresh
        // cannot answer the same grant differently.
        if (!grant.grantedScopes.contains(BuiltInGrantableScopeId.OPENID)) {
            return null
        }

        return customSpanTracer.issueToken(CustomSpanTracer.ID_TOKEN) {
            val authConfig = uncheckedAuthConfig.orThrow()
            val advancedConfig = uncheckedAdvancedConfig.orThrow()

            val publishedClaims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
                userId = userId,
                audienceId = audience.id,
                place = ClaimPublicationPlace.ID_TOKEN,
                doors = doors,
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
                // Claimed on every id token, not only where a client asked with `max_age`; see
                // `docs/design/security.md`.
                grant.authenticationDate?.let {
                    claim(OpenIdConnectClaimId.AUTH_TIME, it.toEpochSecond(ZoneOffset.UTC))
                }
                claim("at_hash", advancedConfig.publicJwtAlgorithm.hashAlgorithm.atHash(accessToken.await().token))
                // Last, and against what this token has already claimed, so that nothing a deployment named
                // after a member the token states about its own authorization displaces it.
                publishedClaims.publishedMembers(reserved = claims.keys).forEach { (name, value) ->
                    claim(name, value)
                }
            }

            tokenMapper.toEncodedAuthenticationToken(entity, grant, encodedToken)
        }
    }
}
