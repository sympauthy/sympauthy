package com.sympauthy.business.manager.auth

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.GrantMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.interactiveFlowSessionConcurrentModificationExceptionOf
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantOpenedForResult
import com.sympauthy.business.model.grant.GrantRevocationCriteria
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.grant.GrantStatus
import com.sympauthy.business.model.grant.GrantWithStatus
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.GrantEntity
import com.sympauthy.data.repository.GrantRepository
import com.sympauthy.monitoring.telemetry.TokenMetrics
import com.sympauthy.util.isRowAlreadyThere
import com.sympauthy.util.wireName
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import java.time.LocalDateTime
import java.util.*

/**
 * Opens the grants tokens are issued under, checks one presented to it, and revokes them.
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
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val tokenMetrics: TokenMetrics
) {

    /**
     * Return the [Grant] identified by [id], null where there is none.
     */
    suspend fun findByIdOrNull(id: UUID): Grant? {
        return grantRepository.findById(id)?.let(grantMapper::toGrant)
    }

    /**
     * Return the [Grant] identified by [id] with where it stands now, null where there is none — which
     * includes a grant the cleanup job has already collected.
     */
    suspend fun findWithStatusByIdOrNull(id: UUID): GrantWithStatus? {
        val now = LocalDateTime.now()
        val grant = findByIdOrNull(id) ?: return null
        val live = grantRepository.findLiveByIdAt(id, now) != null
        return GrantWithStatus(grant, getStatus(grant, live, now))
    }

    /**
     * List every grant this server still holds, revoked, expired and lapsed ones included, each with where it
     * stands now.
     *
     * The grants are read before the ones still live, so a grant opened between the two reads is left out
     * rather than read as lapsed.
     */
    suspend fun listWithStatus(): List<GrantWithStatus> {
        val now = LocalDateTime.now()
        val grants = grantRepository.findAll().toList().map(grantMapper::toGrant)
        val liveIds = grantRepository.findLiveAt(now).mapNotNull(GrantEntity::id).toSet()
        return grants.map { GrantWithStatus(it, getStatus(it, it.id in liveIds, now)) }
    }

    /**
     * Where [grant] stands at [now], given whether it is [live] — whether a revocation written at [now] would
     * reach it.
     *
     * A grant read as not live is told apart by what is recorded on it, in the order [GrantStatus] gives: revoked
     * first, then past its lifetime, and lapsed otherwise.
     */
    internal fun getStatus(grant: Grant, live: Boolean, now: LocalDateTime): GrantStatus = when {
        live -> GrantStatus.LIVE
        grant.revoked -> GrantStatus.REVOKED
        grant.expirationDate?.isBefore(now) == true -> GrantStatus.EXPIRED
        else -> GrantStatus.LAPSED
    }

    /**
     * Whether [grant] was opened for [client], and for the audience [client] belongs to now:
     * [GrantOpenedForResult.OpenedForAnotherClient] where it was opened for another client, and
     * [GrantOpenedForResult.OpenedForAnotherAudience] where [client] has since moved to another audience.
     *
     * A grant is the audience's it was opened for, and that is what revoking a consent reaches it by: tokens
     * issued for an audience its client has since moved to would escape the consent they rest on.
     */
    fun verifyOpenedFor(grant: Grant, client: Client): GrantOpenedForResult = when {
        grant.clientId != client.id -> GrantOpenedForResult.OpenedForAnotherClient(
            grantClientId = grant.clientId,
            clientId = client.id
        )
        grant.audienceId != client.audience.id -> GrantOpenedForResult.OpenedForAnotherAudience(
            grantAudienceId = grant.audienceId,
            clientId = client.id,
            clientAudienceId = client.audience.id
        )
        else -> GrantOpenedForResult.Opened
    }

    /**
     * Refuse [grant] unless it was opened for [client], and for the audience [client] belongs to now, as an
     * `OAuth2Exception` carrying `invalid_grant`: `token.mismatching_client` where it was opened for another
     * client, and `grant.mismatching_audience` where [client] has since moved to another audience.
     */
    fun checkOpenedFor(grant: Grant, client: Client) {
        val openedFor = verifyOpenedFor(grant, client)
        if (openedFor is GrantOpenedForResult.Refused) {
            throw openedFor.toOAuth2Exception()
        }
    }

    /**
     * Refuse [grant] unless it is live, as an `OAuth2Exception` carrying `invalid_grant`: `grant.revoked`
     * where it has been revoked, and `grant.expired` where it is past its lifetime.
     */
    fun checkLive(grant: Grant) {
        when {
            grant.revoked -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.revoked")
            grant.expired -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.expired", "description.grant.expired")
        }
    }

    /**
     * Refuse [grant] where it names a user that is not an account this server has finished creating, as an
     * `OAuth2Exception` carrying `invalid_grant` and `grant.invalid_user`. A grant naming no user passes.
     *
     * A token is what turns an account into one another system will act on, so none is issued under a grant
     * whose account a sign-up has not finished, however the request reached this point. The account is read
     * through the reader of committed accounts alone, which answers an unfinished one as no account at all.
     */
    suspend fun checkUserPromoted(grant: Grant) {
        val userId = grant.userId ?: return
        userManager.findByIdOrNull(userId)
            ?: throw oauth2ExceptionOf(INVALID_GRANT, "grant.invalid_user", "description.grant.invalid_user")
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
     * concurrent one. It throws `auth.interactive_flow_session.concurrent_modification`, as the version check
     * after it would, so it is routed by where the winner left the session.
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
     * as by the client holding [actorToken], for the audience identified by [audienceId] the exchange named.
     *
     * It holds no scope, since an act-as rule grants none, and no authentication date, since nobody proved
     * a credential of the person here. It records the grant of [actorToken], which revoking revokes this
     * grant too.
     *
     * It expires no later than [actorToken]: a `client_credentials` request opens a grant of its own, so that
     * token is the last of its grant, and the act-as grant is refused once it has expired. Lasting no longer
     * caps the `exp` of every token under the act-as grant, which is what a resource server that is not this
     * one stops honouring it by.
     */
    suspend fun createTokenExchangeGrant(
        userId: UUID,
        audienceId: String,
        actorToken: AuthenticationToken
    ): Grant = createGrant(
        userId = userId,
        clientId = actorToken.grant.clientId,
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
        actorGrantId = actorToken.grant.id,
        latestExpirationDate = actorToken.expirationDate
    )

    /**
     * Write a new grant, opened now, and give it the lifetime `auth.grant.expiration` configures, ending no
     * later than [latestExpirationDate] where one is passed.
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
        actorGrantId: UUID?,
        latestExpirationDate: LocalDateTime? = null
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
            expirationDate = listOfNotNull(
                authConfig.grant.expiration?.let(creationDate::plus),
                latestExpirationDate
            ).minOrNull()
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
            revokeWithExchanged(grant, LocalDateTime.now(), revokedBy, revokedById)
        }
    }

    /**
     * Revoke every grant [criteria] reach, and every grant a token exchange opened from one of their tokens,
     * and return how many of them were still live.
     *
     * A grant already revoked, past its lifetime or lapsed is left as it is and not counted, as
     * [revokeGrant] leaves it; the grants opened from it are reached all the same.
     *
     * Throws a recoverable `grant.revoke.no_criterion` where [criteria] is unbounded, rather than revoking every
     * grant this server holds.
     */
    @Transactional
    open suspend fun revokeGrants(
        criteria: GrantRevocationCriteria,
        revokedBy: GrantRevokedBy,
        revokedById: UUID?
    ): Int {
        if (criteria.isUnbounded) {
            throw recoverableBusinessExceptionOf(
                detailsId = "grant.revoke.no_criterion",
                descriptionId = "description.grant.revoke.no_criterion"
            )
        }
        val grants = grantRepository.findAll().toList().map(grantMapper::toGrant).filter(criteria::matches)
        val users = grants.mapNotNull(Grant::userId).distinct().map(LockKey::User)
        return lockManager.withLock(users) {
            val now = LocalDateTime.now()
            grants.sumOf { revokeWithExchanged(it, now, revokedBy, revokedById) }
        }
    }

    /**
     * Revoke [grant] and every grant a token exchange opened from one of its tokens at [revokedAt], each only
     * where it is still live, and return how many were.
     *
     * The caller holds the lock over the account [grant] is opened for.
     */
    internal suspend fun revokeWithExchanged(
        grant: Grant,
        revokedAt: LocalDateTime,
        revokedBy: GrantRevokedBy,
        revokedById: UUID?
    ): Int {
        val revoked = grantRepository.updateRevokedAt(
            id = grant.id,
            revokedAt = revokedAt,
            revokedBy = revokedBy.name,
            revokedById = revokedById
        )
        val actorGrantsRevoked = grantRepository.updateRevokedAtByActorGrantId(
            actorGrantId = grant.id,
            revokedAt = revokedAt,
            revokedBy = revokedBy.name,
            revokedById = revokedById
        )
        tokenMetrics.recordRevocations(revokedBy.metricName, grant.clientId, revoked)
        // An act-as grant is opened for whichever client acted, so the ones opened from this grant span clients.
        tokenMetrics.recordRevocations(revokedBy.metricName, null, actorGrantsRevoked)
        return revoked + actorGrantsRevoked
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
        }.also { tokenMetrics.recordRevocations(revokedBy.metricName, null, it) }
    }
}

/**
 * What a revocation is recorded under on `revoked_by`.
 */
private val GrantRevokedBy.metricName: String
    get() = name.lowercase()

/**
 * This refusal as the protocol error the token endpoint answers it with: `invalid_grant`, under the code, the
 * description and the values of the refusal.
 */
fun GrantOpenedForResult.Refused.toOAuth2Exception() = OAuth2Exception(
    errorCode = INVALID_GRANT,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values
)
