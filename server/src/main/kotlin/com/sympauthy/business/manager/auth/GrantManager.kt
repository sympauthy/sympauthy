package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.GrantMapper
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.interactiveFlowSessionConcurrentModificationExceptionOf
import com.sympauthy.business.model.oauth2.Grant
import com.sympauthy.business.model.oauth2.GrantRevokedBy
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.GrantEntity
import com.sympauthy.data.repository.GrantRepository
import com.sympauthy.util.isRowAlreadyThere
import com.sympauthy.util.wireName
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

/**
 * Opens the grants tokens are issued under, and revokes them.
 *
 * Every revocation reaches grants rather than tokens, and only a grant still live: one not revoked yet and
 * holding a token that has not expired. A grant already revoked keeps who revoked it first, and one that
 * lapsed is not recorded as revoked afterwards, so a revocation that counts answers how many grants it took
 * out of service.
 *
 * Every revocation of a person's grants is written holding the lock over their account, which a refresh of
 * their tokens takes too. A revocation reads a grant live from the tokens committed under it, so without the
 * lock a refresh renewing a grant whose last token is about to expire could have its tokens written after
 * the revocation judged the grant lapsed, and left live. See `docs/design/grants.md`.
 */
@Singleton
open class GrantManager(
    @Inject private val grantRepository: GrantRepository,
    @Inject private val grantMapper: GrantMapper,
    @Inject private val lockManager: LockManager,
    @Inject private val userManager: UserManager,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Return the [Grant] identified by [id], null where there is none.
     */
    suspend fun findByIdOrNull(id: UUID): Grant? {
        return grantRepository.findById(id)?.let(grantMapper::toGrant)
    }

    /**
     * Open the grant the authorization [session] completes issues its code under, from what the session's
     * [oauth2] request record holds once the flow is complete: the scopes granted and consented, when [userId]
     * authenticated, and the claims the request named. Every token the exchange of that code issues, and every
     * refresh descended from them, stands under it.
     *
     * [audienceId] identifies the audience the grant is opened for, which is the audience of the client the
     * authorization was requested by.
     *
     * A session opens one grant: the unique index over the session a grant came from refuses a second, and
     * with the grant written as the session completes, the request refused is one whose completion lost to a
     * concurrent one. It throws `auth.interactive_flow_session.concurrent_modification`, as the code written
     * after it and the version check after that would, so it is routed by where the winner left the session.
     * Every other way the write can be refused travels on as this server's own failure.
     */
    suspend fun createAuthorizationCodeGrant(
        session: InteractiveFlowSession,
        oauth2: InteractiveFlowSessionOAuth2,
        userId: UUID,
        audienceId: String
    ): Grant = try {
        createGrant(
            userId = userId,
            clientId = oauth2.clientId,
            audienceId = audienceId,
            grantType = GrantType.AUTHORIZATION_CODE,
            grantedScopes = oauth2.grantedScopes ?: emptyList(),
            grantedAt = oauth2.grantedAt,
            grantedBy = oauth2.grantedBy?.name,
            consentedScopes = oauth2.consentedScopes ?: emptyList(),
            consentedAt = oauth2.consentedAt,
            consentedBy = oauth2.consentedBy?.name,
            clientScopes = emptyList(),
            authenticationDate = oauth2.authenticationDate,
            requestedClaims = oauth2.requestedClaims,
            sessionId = session.id,
            actorGrantId = null
        )
    } catch (failure: Exception) {
        if (!failure.isRowAlreadyThere()) {
            throw failure
        }
        throw interactiveFlowSessionConcurrentModificationExceptionOf(session)
    }

    /**
     * Open the grant a `client_credentials` request issues its token under, holding the [clientScopes]
     * granted to the client identified by [clientId] and no person.
     *
     * [audienceId] identifies the audience the grant is opened for, which is the client's own.
     */
    suspend fun createClientCredentialsGrant(
        clientId: String,
        audienceId: String,
        clientScopes: List<String>
    ): Grant = createGrant(
        userId = null,
        clientId = clientId,
        audienceId = audienceId,
        grantType = GrantType.CLIENT_CREDENTIALS,
        grantedScopes = emptyList(),
        grantedAt = null,
        grantedBy = null,
        consentedScopes = emptyList(),
        consentedAt = null,
        consentedBy = null,
        clientScopes = clientScopes,
        authenticationDate = null,
        requestedClaims = RequestedClaims.NONE,
        sessionId = null,
        actorGrantId = null
    )

    /**
     * Open the grant a token exchange (RFC 8693) issues its act-as token under: the person [userId] acted
     * as by the client holding [actorGrant], for the audience identified by [audienceId] the exchange named.
     *
     * It holds no scope, since an act-as rule grants none, and no authentication date, since nobody proved
     * a credential of the person here. It records [actorGrant], which revoking revokes this grant too.
     */
    suspend fun createTokenExchangeGrant(
        userId: UUID,
        audienceId: String,
        actorGrant: Grant
    ): Grant = createGrant(
        userId = userId,
        clientId = actorGrant.clientId,
        audienceId = audienceId,
        grantType = GrantType.TOKEN_EXCHANGE,
        grantedScopes = emptyList(),
        grantedAt = null,
        grantedBy = null,
        consentedScopes = emptyList(),
        consentedAt = null,
        consentedBy = null,
        clientScopes = emptyList(),
        authenticationDate = null,
        requestedClaims = RequestedClaims.NONE,
        sessionId = null,
        actorGrantId = actorGrant.id
    )

    /**
     * Write a new grant, opened now, and give it the lifetime `auth.token.grant-expiration` configures.
     *
     * Throws `user.not_found` or `user.not_promoted` where [userId] names no account this server has finished
     * creating, as [UserManager.checkPromoted] does: a grant outlives the session that may still be signing
     * the account up, and every token issued under it would hang on that account.
     */
    @Suppress("LongParameterList")
    internal suspend fun createGrant(
        userId: UUID?,
        clientId: String,
        audienceId: String,
        grantType: GrantType,
        grantedScopes: List<String>,
        grantedAt: LocalDateTime?,
        grantedBy: String?,
        consentedScopes: List<String>,
        consentedAt: LocalDateTime?,
        consentedBy: String?,
        clientScopes: List<String>,
        authenticationDate: LocalDateTime?,
        requestedClaims: RequestedClaims,
        sessionId: UUID?,
        actorGrantId: UUID?
    ): Grant {
        userId?.let { userManager.checkPromoted(it) }
        val authConfig = uncheckedAuthConfig.orThrow()
        val creationDate = LocalDateTime.now()
        val entity = GrantEntity(
            userId = userId,
            clientId = clientId,
            audienceId = audienceId,
            grantType = grantType.wireName,
            grantedScopes = grantedScopes.toTypedArray(),
            grantedAt = grantedAt,
            grantedBy = grantedBy,
            consentedScopes = consentedScopes.toTypedArray(),
            consentedAt = consentedAt,
            consentedBy = consentedBy,
            clientScopes = clientScopes.toTypedArray(),
            authenticationDate = authenticationDate,
            requestedIdTokenClaims = requestedClaims.idTokenClaimIds.toTypedArray(),
            requestedUserinfoClaims = requestedClaims.userInfoClaimIds.toTypedArray(),
            sessionId = sessionId,
            actorGrantId = actorGrantId,
            creationDate = creationDate,
            expirationDate = authConfig.token.grantExpiration?.let(creationDate::plus)
        )
        return grantMapper.toGrant(grantRepository.save(entity))
    }

    /**
     * Revoke the [grant], and every grant a token exchange opened from one of its tokens.
     *
     * A grant already revoked keeps who revoked it first and when, and one that lapsed is left as it lapsed;
     * either way nothing is written to it, and nothing to the grants opened from it that are in the same
     * state.
     */
    @Transactional
    open suspend fun revokeGrant(grant: Grant, revokedBy: GrantRevokedBy, revokedById: UUID?) {
        lockManager.withLock(listOfNotNull(grant.userId?.let(LockKey::User))) {
            val now = LocalDateTime.now()
            grantRepository.updateRevokedAt(
                id = grant.id,
                revokedAt = now,
                revokedBy = revokedBy.name,
                revokedById = revokedById
            )
            grantRepository.updateRevokedAtByActorGrantId(
                actorGrantId = grant.id,
                revokedAt = now,
                revokedBy = revokedBy.name,
                revokedById = revokedById
            )
        }
    }

    /**
     * Revoke every grant of [userId] that is still live, regardless of client, and return how many there
     * were.
     */
    @Transactional
    open suspend fun revokeGrantsByUser(userId: UUID, revokedBy: GrantRevokedBy, revokedById: UUID?): Int {
        return lockManager.withLock(listOf(LockKey.User(userId))) {
            grantRepository.updateRevokedAtByUserId(
                userId = userId,
                revokedAt = LocalDateTime.now(),
                revokedBy = revokedBy.name,
                revokedById = revokedById
            )
        }
    }

    /**
     * Revoke every grant of [userId] opened for [clientId] that is still live, and return how many there
     * were.
     */
    @Transactional
    open suspend fun revokeGrantsByUserAndClient(
        userId: UUID,
        clientId: String,
        revokedBy: GrantRevokedBy,
        revokedById: UUID?
    ): Int {
        return lockManager.withLock(listOf(LockKey.User(userId))) {
            grantRepository.updateRevokedAtByUserIdAndClientId(
                userId = userId,
                clientId = clientId,
                revokedAt = LocalDateTime.now(),
                revokedBy = revokedBy.name,
                revokedById = revokedById
            )
        }
    }

    /**
     * Revoke every grant of [userId] opened for the audience identified by [audienceId] that is still live,
     * except the act-as ones, recording it as revoked at [revokedAt], and return how many there were.
     *
     * An act-as grant rests on the act-as rules rather than on anything the person agreed to, so nothing a
     * person withdraws for an audience reaches one.
     */
    @Transactional
    open suspend fun revokeGrantsByUserAndAudience(
        userId: UUID,
        audienceId: String,
        revokedAt: LocalDateTime,
        revokedBy: GrantRevokedBy,
        revokedById: UUID?
    ): Int {
        return lockManager.withLock(listOf(LockKey.User(userId))) {
            grantRepository.updateRevokedAtByUserIdAndAudienceIdAndActorGrantIdIsNull(
                userId = userId,
                audienceId = audienceId,
                revokedAt = revokedAt,
                revokedBy = revokedBy.name,
                revokedById = revokedById
            )
        }
    }
}
