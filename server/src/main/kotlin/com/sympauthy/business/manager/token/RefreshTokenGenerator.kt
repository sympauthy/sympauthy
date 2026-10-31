package com.sympauthy.business.manager.token

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.token.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.token.EncodedAuthenticationToken
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

@Singleton
class RefreshTokenGenerator(
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: EncodedAuthenticationTokenMapper,
    @Inject private val authConfig: AuthConfig,
    @Inject private val customSpanTracer: CustomSpanTracer
) {

    /**
     * Generate a new refresh token under the [grant], recorded as issued by [grantType] — the grant type that
     * opened the [grant], unless a refresh issues it — or return null if the refresh token is disabled by the
     * [authConfig].
     *
     * Nothing about the authorization is claimed in it — nothing reads a refresh token but this server, and
     * every token the next refresh mints reads the [grant] — beyond the subject and the [audience] it was
     * issued for.
     *
     * Its row expires no later than the [grant] does, which is what lets a grant past its lifetime lapse and
     * be collected. The `exp` it is signed with is its own lifetime instead, so that one presented past the
     * grant's end, and before the grant is collected, is refused by the grant, as `grant.expired`, rather than
     * by the signature check as a token that ran out on its own.
     */
    suspend fun generateRefreshToken(
        grant: Grant,
        audience: Audience,
        grantType: GrantType = grant.grantType,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken? {
        val enabledAuthConfig = authConfig.orThrow()
        if (!enabledAuthConfig.token.refreshEnabled) {
            return null
        }

        return customSpanTracer.issueToken(CustomSpanTracer.REFRESH_TOKEN) {
            val issueDate = LocalDateTime.now()
            val ownExpirationDate = enabledAuthConfig.token.refreshExpiration?.let(issueDate::plus)
            val entity = AuthenticationTokenEntity(
                type = REFRESH.name,
                grantId = grant.id,
                grantType = grantType.wireName,
                dpopJkt = dpopJkt,
                issueDate = issueDate,
                expirationDate = grant.getTokenExpirationDate(ownExpirationDate)
            ).let { tokenRepository.save(it) }

            val encodedToken = jwtManager.create(JwtManager.REFRESH_KEY) {
                entity.id?.toString()?.let(this::jwtID)
                audience(listOf(audience.tokenAudience))
                subject(grant.userId?.toString() ?: grant.clientId)
                issueTime(Date.from(issueDate.toInstant(ZoneOffset.UTC)))
                ownExpirationDate?.toInstant(ZoneOffset.UTC)?.let { expirationTime(Date.from(it)) }
            }

            tokenMapper.toEncodedAuthenticationToken(entity, grant, encodedToken)
        }
    }
}
