package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jwt.JWTClaimsSet
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.util.loggerForClass
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Singleton
class IdTokenGenerator(
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: EncodedAuthenticationTokenMapper,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    private val logger = loggerForClass()

    /**
     * Generate a new id token containing user info accessible according to the scopes granted in [authorizeAttempt].
     * Only claims the end-user has consented to share with the client are included.
     */
    suspend fun generateIdToken(
        authorizeAttempt: CompletedAuthorizeAttempt,
        userId: UUID,
        accessToken: EncodedAuthenticationToken
    ) = generateIdToken(
        userId = userId,
        authorizeAttemptId = authorizeAttempt.id,
        clientId = authorizeAttempt.clientId,
        grantedScopes = authorizeAttempt.grantedScopes,
        consentedScopes = authorizeAttempt.consentedScopes,
        nonce = authorizeAttempt.nonce,
        accessToken = accessToken,
        grantType = "authorization_code"
    )

    /**
     * Generate a new id token using the information stored in a [refreshToken].
     * Only claims the end-user has consented to share with the client are included.
     */
    suspend fun generateIdToken(
        refreshToken: AuthenticationToken,
        accessToken: EncodedAuthenticationToken
    ) = generateIdToken(
        userId = refreshToken.userId,
        clientId = refreshToken.clientId,
        grantedScopes = refreshToken.grantedScopes,
        consentedScopes = refreshToken.consentedScopes,
        authorizeAttemptId = refreshToken.authorizeAttemptId,
        accessToken = accessToken,
        grantType = "refresh_token"
    )

    internal suspend fun generateIdToken(
        userId: UUID?,
        clientId: String,
        grantedScopes: List<String>,
        consentedScopes: List<String>,
        authorizeAttemptId: UUID?,
        accessToken: EncodedAuthenticationToken,
        nonce: String? = null,
        grantType: String
    ): EncodedAuthenticationToken? {
        // ID tokens are only for user authentication, not client credentials
        if (userId == null) {
            return null
        }

        val authConfig = uncheckedAuthConfig.orThrow()

        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
            userId = userId,
            consentedScopes = consentedScopes
        )

        val issueDate = LocalDateTime.now()
        val expirationDate = issueDate.plus(authConfig.token.idExpiration)
        val entity = AuthenticationTokenEntity(
            userId = userId,
            type = AuthenticationTokenType.ID.name,
            clientId = clientId,
            grantedScopes = grantedScopes.toTypedArray(),
            consentedScopes = consentedScopes.toTypedArray(),
            clientScopes = emptyArray(),
            authorizeAttemptId = authorizeAttemptId,
            grantType = grantType,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(JwtManager.PUBLIC_KEY) {
            entity.id?.toString()?.let(this::jwtID)
            // Pretty weird but in OpenID spec, the audience is the client_id of the client which defer from OAuth2 spec.
            // https://openid.net/specs/openid-connect-basic-1_0.html#IDToken
            audience(listOf(clientId))
            subject(userId.toString())
            issueTime(Date.from(issueDate.toInstant(ZoneOffset.UTC)))
            expirationTime(Date.from(expirationDate.toInstant(ZoneOffset.UTC)))
            nonce?.let { claim("nonce", it) }

            val (addressClaims, otherClaims) = claims.partition { it.claim.group == ClaimGroup.ADDRESS }
            otherClaims.forEach { claim ->
                withClaim(claim)
            }
            withAddressClaim(addressClaims)
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }

    internal fun shouldGenerateIdToken(scopes: List<String>): Boolean {
        return scopes.contains(BuiltInGrantableScopeId.OPENID)
    }

    private fun JWTClaimsSet.Builder.withClaim(claim: CollectedClaim) {
        when (claim.value) {
            is String -> claim(claim.claim.id, claim.value)
            else -> {
                logger.error("Unable to encode claim '${claim.claim.id}' into id token.")
            }
        }
        if (claim.claim.verifiedId != null) {
            claim(claim.claim.verifiedId, claim.verified ?: false)
        }
    }

    private fun JWTClaimsSet.Builder.withAddressClaim(addressClaims: List<CollectedClaim>) {
        if (addressClaims.isEmpty()) return
        val addressMap = mutableMapOf<String, Any>()
        addressClaims.forEach { claim ->
            val value = claim.value
            if (value is String) {
                addressMap[claim.claim.id] = value
            }
        }
        if (addressMap.isNotEmpty()) {
            val formatted = listOfNotNull(
                addressMap["street_address"] as? String,
                listOfNotNull(
                    addressMap["locality"] as? String,
                    addressMap["region"] as? String,
                    addressMap["postal_code"] as? String
                ).joinToString(", ").ifBlank { null },
                addressMap["country"] as? String
            ).joinToString("\n").ifBlank { null }
            formatted?.let { addressMap["formatted"] = it }
            claim("address", addressMap)
        }
    }
}
