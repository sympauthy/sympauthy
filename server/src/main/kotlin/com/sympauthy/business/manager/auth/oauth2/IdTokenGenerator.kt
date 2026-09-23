package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jwt.JWTClaimsSet
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.*
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.config.model.AdvancedConfig
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
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: EncodedAuthenticationTokenMapper,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    private val logger = loggerForClass()

    /**
     * Generate a new id token containing user info accessible according to the scopes granted in the
     * session's [oauth2] request record. Only claims the end-user has consented to share with the client
     * are included.
     *
     * [audienceId] is the audience the client belongs to, and a claim restricted to another one is left out.
     * It is not the token's own `aud`, which OpenID Connect fixes at the client id.
     *
     * [accessToken] is the one issued in the same response, and the token's `at_hash` claim names it.
     *
     * Returns null where the grant [oauth2] records does not carry `openid`.
     */
    suspend fun generateIdToken(
        oauth2: InteractiveFlowSessionOAuth2,
        userId: UUID,
        audienceId: String,
        accessToken: EncodedAuthenticationToken
    ) = generateIdToken(
        userId = userId,
        sessionId = oauth2.sessionId,
        clientId = oauth2.clientId,
        audienceId = audienceId,
        grantedScopes = oauth2.grantedScopes ?: emptyList(),
        consentedScopes = oauth2.consentedScopes ?: emptyList(),
        nonce = oauth2.nonce,
        accessToken = accessToken,
        grantType = "authorization_code"
    )

    /**
     * Generate a new id token using the information stored in a [refreshToken].
     * Only claims the end-user has consented to share with the client are included.
     *
     * [audienceId] is the audience the client belongs to, and a claim restricted to another one is left out.
     * It is not the token's own `aud`, which OpenID Connect fixes at the client id.
     *
     * [accessToken] is the one issued in the same response, and the token's `at_hash` claim names it.
     *
     * The subject, the audience and the session are the ones of the authentication the [refreshToken]
     * descends from, and only the issue date, the expiry and the claims are read again, which is what
     * OpenID Connect Core §12.2 requires of an id token issued for a refresh.
     *
     * No `nonce` is claimed, which §12.2 asks for in as many words: a refreshed id token "SHOULD NOT
     * have a `nonce` Claim, even when the ID Token issued at the time of the original authentication
     * contained `nonce`". A nonce binds an id token to an authorization request, and no authorization
     * request was made here.
     *
     * Returns null unless the grant the [refreshToken] descends from names a user and carries `openid`: a
     * `client_credentials` grant has no identity to assert, and one that never asked for OpenID Connect
     * asked for none.
     */
    suspend fun generateIdToken(
        refreshToken: AuthenticationToken,
        audienceId: String,
        accessToken: EncodedAuthenticationToken
    ) = generateIdToken(
        userId = refreshToken.userId,
        clientId = refreshToken.clientId,
        audienceId = audienceId,
        grantedScopes = refreshToken.grantedScopes,
        consentedScopes = refreshToken.consentedScopes,
        sessionId = refreshToken.sessionId,
        accessToken = accessToken,
        grantType = "refresh_token"
    )

    internal suspend fun generateIdToken(
        userId: UUID?,
        clientId: String,
        audienceId: String,
        grantedScopes: List<String>,
        consentedScopes: List<String>,
        sessionId: UUID?,
        accessToken: EncodedAuthenticationToken,
        nonce: String? = null,
        grantType: String
    ): EncodedAuthenticationToken? {
        // ID tokens are only for user authentication, not client credentials
        if (userId == null) {
            return null
        }
        // An id token answers a request for `openid`, and a grant that never asked for OpenID Connect is
        // owed none. Every caller passes through here, so the authorization code and the refresh cannot
        // answer the same grant differently.
        if (!grantedScopes.contains(BuiltInGrantableScopeId.OPENID)) {
            return null
        }

        val authConfig = uncheckedAuthConfig.orThrow()
        val advancedConfig = uncheckedAdvancedConfig.orThrow()

        val claims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
            userId = userId,
            audienceId = audienceId,
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
            sessionId = sessionId,
            grantType = grantType,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(JwtManager.PUBLIC_KEY) {
            entity.id?.toString()?.let(this::jwtID)
            // Pretty weird but in OpenID spec, the audience is the client_id of the client which defer from OAuth2
            // spec.
            // https://openid.net/specs/openid-connect-basic-1_0.html#IDToken
            audience(listOf(clientId))
            subject(generatedClaimsManager.computeSubject(userId))
            issueTime(Date.from(issueDate.toInstant(ZoneOffset.UTC)))
            expirationTime(Date.from(expirationDate.toInstant(ZoneOffset.UTC)))
            nonce?.let { claim("nonce", it) }
            claim("at_hash", advancedConfig.publicJwtAlgorithm.hashAlgorithm.atHash(accessToken.token))

            val (addressClaims, otherClaims) = claims.partition { it.claim.group == ClaimGroup.ADDRESS }
            otherClaims.forEach { claim ->
                withClaim(claim)
            }
            withAddressClaim(addressClaims)
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }

    /**
     * Claim [claim] on this token, and its `<claim>_verified` companion beside it where the claim declares
     * one. A claim holding no value is not claimed at all, and neither is one whose value this server has
     * no wire form for.
     *
     * The companion is claimed only beside a value, because `foo_verified: true` with no `foo` is this
     * server asserting it verified something it did not send.
     */
    private fun JWTClaimsSet.Builder.withClaim(claim: CollectedClaim) {
        val value = claim.value ?: return
        val encoded = encodeOrNull(claim.claim.dataType, value)
        if (encoded == null) {
            logger.error("Unable to encode claim '${claim.claim.id}' into id token.")
            return
        }
        claim(claim.claim.id, encoded)
        claim.claim.verifiedId?.let { claim(it, claim.verified ?: false) }
    }

    /**
     * Claim the `address` object OpenID Connect Core §5.1.1 defines, assembled from the [addressClaims] of
     * the group, or nothing where none of them carries a value this server can encode.
     *
     * Every member of that object is a string there, whatever type the claim behind it was configured as,
     * so a component is rendered rather than left out — a `postal_code` configured as a number belongs in
     * the object and in the `formatted` line as much as one configured as a string.
     */
    private fun JWTClaimsSet.Builder.withAddressClaim(addressClaims: List<CollectedClaim>) {
        if (addressClaims.isEmpty()) return
        val addressMap = mutableMapOf<String, String>()
        addressClaims.forEach { claim ->
            val value = claim.value ?: return@forEach
            val encoded = encodeOrNull(claim.claim.dataType, value)
            if (encoded == null) {
                logger.error("Unable to encode claim '${claim.claim.id}' into the address of an id token.")
                return@forEach
            }
            addressMap[claim.claim.id] = encoded.toString()
        }
        if (addressMap.isNotEmpty()) {
            val formatted = listOfNotNull(
                addressMap["street_address"],
                listOfNotNull(
                    addressMap["locality"],
                    addressMap["region"],
                    addressMap["postal_code"]
                ).joinToString(", ").ifBlank { null },
                addressMap["country"]
            ).joinToString("\n").ifBlank { null }
            formatted?.let { addressMap["formatted"] = it }
            claim("address", addressMap)
        }
    }
}

/**
 * [value] as the JSON type a claim of [dataType] is published as, or null where this server publishes no
 * value of that type in an id token, or where the value is not one of that type after all.
 *
 * What decides the wire form is the type a deployment declared, exhaustively, and never the type the value
 * happens to be carrying. The second is an artifact of how the value round-tripped through the object
 * mapper, and reading the wire form off it is how `number` came to be absent from every id token ever
 * issued — with nothing to notice it but an error line per claim per token.
 *
 * The narrowing that remains is a belt-and-braces check rather than a decision.
 * [CollectedClaimMapper][com.sympauthy.business.mapper.CollectedClaimMapper] already drops a row that
 * does not read back as its claim's type, so nothing should reach here disagreeing with it, and the
 * caller logs it rather than publishing whatever it turned out to be.
 */
private fun encodeOrNull(dataType: ClaimDataType, value: Any): Any? = when (dataType) {
    BOOLEAN -> value as? Boolean
    NUMBER -> (value as? Number)?.toLong()
    DATE, EMAIL, PHONE_NUMBER, STRING, TIMEZONE -> value as? String
}
