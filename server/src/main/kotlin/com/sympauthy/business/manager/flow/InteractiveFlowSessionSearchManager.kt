package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMapper
import com.sympauthy.business.mapper.InteractiveFlowSessionSecurityContextMapper
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeProgress
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStatus
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionSecurityContext
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.SortOrder
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.page.orderedPage
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import java.time.LocalDateTime
import java.util.*

/**
 * Reads the interactive flow sessions this server currently holds, for an operator debugging one.
 *
 * **It reads the rows rather than the sealed [InteractiveFlowSession] the flow passes around**, and that is
 * what makes the status honest. [InteractiveFlowSessionMapper.toInteractiveFlowSession] puts expiry ahead of
 * cancellation and completion, so a session that completed and has since passed its expiration projects as a
 * failed one — correct for the engine, which is deciding what to do with the session now, and wrong here,
 * where somebody is asking what happened. The status is therefore
 * [InteractiveFlowSessionMapper.toStatus]'s, and where the sealed model is needed it is built for that status
 * through [InteractiveFlowSessionMapper.toInteractiveFlowSessionAs].
 *
 * **The search runs in memory**, as every other admin listing does — see
 * [com.sympauthy.business.manager.user.UserSearchManager] for the reason. This set is bounded by the session
 * expiry window rather than by how long the deployment has been running, so it is smaller than the user table
 * the same approach already serves.
 *
 * **The search itself is four reads.** Every session and every observation, then — once the order has decided
 * which rows are published — the users of that page and their identifier claims. A session the criteria kept
 * but the page left off costs nothing beyond the row it was read from.
 *
 * **What is published costs more, per row, and deliberately.** Answering which purpose a session is stopped
 * at is the engine's walk, and the walk asks every handler in turn — so a page of twenty costs twenty walks,
 * not one read. That is the question the endpoint exists to answer and it is not stored anywhere; keeping it
 * on the page rather than on everything the criteria kept is what bounds it.
 *
 * **It reads no attached record for the listing.** The client is a column on the session, so `q` and every
 * filter match against the session row and its one observation. The detail reads the attached records, one
 * session at a time, through the handlers that own them.
 */
@Singleton
class InteractiveFlowSessionSearchManager(
    @Inject private val sessionRepository: InteractiveFlowSessionRepository,
    @Inject private val securityContextRepository: InteractiveFlowSessionSecurityContextRepository,
    @Inject private val userManager: UserManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val sessionMapper: InteractiveFlowSessionMapper,
    @Inject private val securityContextMapper: InteractiveFlowSessionSecurityContextMapper,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val purposeRegistry: InteractiveFlowPurposeRegistry
) {

    /**
     * Read the page [pageParams] names of the sessions the criteria keep.
     *
     * Every criterion is optional and they compose: [query] is a partial, case-insensitive match across the
     * observed address, the observed user agent and the initiating client id; [clientId], [userId], [purpose]
     * and [status] are exact. [order] is the direction the page is read in, ascending where the caller named
     * none.
     *
     * The order is the session date then the session's identifier, so it is total and a new session appends at
     * the tail. The identifier stays ascending under [SortOrder.DESC]: it is not what the caller asked to sort
     * by, it is there to decide what the date leaves undecided.
     */
    suspend fun listSessions(
        query: String?,
        clientId: String?,
        userId: UUID?,
        purpose: InteractiveFlowPurpose?,
        status: InteractiveFlowSessionStatus?,
        order: SortOrder?,
        pageParams: PageParams
    ): Page<InteractiveFlowSessionSummary> {
        val securityContexts = securityContextRepository.findAll().toList()
            .associate { it.sessionId to securityContextMapper.toInteractiveFlowSessionSecurityContext(it) }

        val entitiesById = sessionRepository.findAll().toList().associateBy(sessionMapper::toId)

        val rows = entitiesById.map { (id, entity) ->
            SearchedInteractiveFlowSession(
                id = id,
                status = sessionMapper.toStatus(entity),
                initiatingPurpose = sessionMapper.toInitiatingPurpose(entity),
                initiatingClientId = entity.initiatingClientId,
                sessionDate = entity.sessionDate,
                expirationDate = entity.expirationDate,
                userId = entity.userId,
                signedUp = entity.signedUp,
                securityContext = securityContexts[id]
            )
        }

        val page = rows.filter { keeps(it, query, clientId, userId, purpose, status) }
            .orderedPage(pageParams, comparatorOf(order))
        val users = readUsersOf(page.items.mapNotNull(SearchedInteractiveFlowSession::userId))
        // The sealed model is built for the page and not beside every row: it is read only to walk the
        // purposes of what is published, so a session the criteria kept but the order left off pays for
        // neither the mapping nor the walk — and a row that cannot be read back cannot take down a page it
        // does not appear on.
        return page.map { searched ->
            val session = sessionMapper.toInteractiveFlowSessionAs(
                entitiesById.getValue(searched.id),
                searched.status
            )
            toSummary(searched, session, users)
        }
    }

    /**
     * Read the session [id], every purpose it carries and where each one stands, or null where no session
     * holds that identifier — which, past the expiry window, is every session this server ever ran.
     *
     * Each purpose is described by the handler that owns it. Nothing catches around those calls: a handler
     * that cannot describe a session is a bug to see rather than a purpose that quietly says nothing.
     */
    suspend fun findSessionOrNull(id: UUID): InteractiveFlowSessionDetail? {
        val entity = sessionRepository.findById(id) ?: return null
        val status = sessionMapper.toStatus(entity)
        val session = sessionMapper.toInteractiveFlowSessionAs(entity, status)
        val completedPurposes = sessionMapper.toCompletedPurposes(entity).toSet()
        val currentPurpose = currentPurposeOrNull(session)

        return InteractiveFlowSessionDetail(
            id = session.id,
            status = status,
            initiatingPurpose = session.initiatingPurpose,
            initiatingClientId = session.initiatingClientId,
            flowId = session.flowId,
            user = entity.userId?.let { readUsersOf(listOf(it))[it] },
            signedUp = entity.signedUp,
            sessionDate = entity.sessionDate,
            expirationDate = entity.expirationDate,
            securityContext = securityContextRepository.findBySessionId(id)
                ?.let(securityContextMapper::toInteractiveFlowSessionSecurityContext),
            errorDetailsId = entity.errorDetailsId,
            errorDescriptionId = entity.errorDescriptionId,
            errorValues = entity.errorValues,
            purposes = session.purposes.map { purpose ->
                InteractiveFlowPurposeProgress(
                    purpose = purpose,
                    status = when {
                        purpose in completedPurposes -> InteractiveFlowPurposeStatus.COMPLETED
                        purpose == currentPurpose -> InteractiveFlowPurposeStatus.CURRENT
                        else -> InteractiveFlowPurposeStatus.PENDING
                    },
                    debugInformation = purposeRegistry.getForPurpose(purpose).debugInformation(session)
                )
            }
        )
    }

    /**
     * The purpose driving [session], or null where none does — which is every terminal session, since the
     * engine's walk is over an ongoing one — and where the walk could not answer.
     *
     * A session that was ongoing when it expired is built as ongoing and does have one: which purpose it
     * stalled on is the question an operator opened it with.
     *
     * **The walk's failure is caught here, unlike a handler's refusal to describe a session.** The two look
     * alike and are not: `nextStepOrNull` answers to the engine, which drives a live flow where a missing
     * attached record or a client the configuration has dropped is a genuine failure and must stop the
     * person — whereas this listing only asks it a question, about somebody else's session, and one row the
     * engine would refuse to advance must not take down the page every other row is on.
     * `debugInformation` was written for this endpoint and its contract is to answer, so nothing catches
     * around it.
     */
    internal suspend fun currentPurposeOrNull(session: InteractiveFlowSession): InteractiveFlowPurpose? {
        val ongoing = session as? OnGoingInteractiveFlowSession ?: return null
        return try {
            engine.currentPurposeOrNull(ongoing)
        } catch (_: BusinessException) {
            null
        }
    }

    /**
     * Whether [searched] passes every criterion the caller named. A criterion left null keeps everything.
     */
    private fun keeps(
        searched: SearchedInteractiveFlowSession,
        query: String?,
        clientId: String?,
        userId: UUID?,
        purpose: InteractiveFlowPurpose?,
        status: InteractiveFlowSessionStatus?
    ): Boolean {
        if (clientId != null && searched.initiatingClientId != clientId) return false
        if (userId != null && searched.userId != userId) return false
        if (purpose != null && searched.initiatingPurpose != purpose) return false
        if (status != null && searched.status != status) return false
        if (query.isNullOrBlank()) return true

        val lowerQuery = query.lowercase()
        return listOfNotNull(
            searched.securityContext?.ip,
            searched.securityContext?.userAgent,
            searched.initiatingClientId
        ).any { it.lowercase().contains(lowerQuery) }
    }

    /**
     * Build the order a page of [listSessions] results is returned in.
     */
    private fun comparatorOf(order: SortOrder?): Comparator<SearchedInteractiveFlowSession> {
        val byDate = compareBy<SearchedInteractiveFlowSession> { it.sessionDate }
        return (if (order == SortOrder.DESC) byDate.reversed() else byDate)
            .thenBy { it.id }
    }

    /**
     * The accounts [userIds] name, with their identifier claims, keyed by identifier.
     *
     * Two queries whatever the size of the page, through the two managers that own those reads —
     * [UserManager.listByIds] answers **committed accounts only**, because an account a session is still
     * signing up is one this server has not finished creating and no reader but that session may have it
     * (see [com.sympauthy.data.model.SessionScoped]), and [CollectedClaimManager.listIdentifierByUserIds]
     * answers what this deployment identifies a person by. A session mid-sign-up therefore names no user
     * here, which is what `signedUp` beside it is for.
     */
    private suspend fun readUsersOf(userIds: List<UUID>): Map<UUID, InteractiveFlowSessionUser> {
        if (userIds.isEmpty()) return emptyMap()
        val users = userManager.listByIds(userIds.distinct())
        if (users.isEmpty()) return emptyMap()

        val claimsByUserId = collectedClaimManager.listIdentifierByUserIds(users.map(User::id))
            .groupBy(CollectedClaim::userId)

        return users.associate { user ->
            user.id to InteractiveFlowSessionUser(
                user = user,
                identifierClaims = claimsByUserId[user.id] ?: emptyList()
            )
        }
    }

    /**
     * The row [searched] is published as, with the purpose driving it and the account it belongs to.
     *
     * Called on the sessions of one page rather than on everything the criteria kept: the walk that answers
     * the current purpose asks every handler, so a session the order left off costs none of those reads.
     */
    private suspend fun toSummary(
        searched: SearchedInteractiveFlowSession,
        session: InteractiveFlowSession,
        users: Map<UUID, InteractiveFlowSessionUser>
    ) = InteractiveFlowSessionSummary(
        id = searched.id,
        status = searched.status,
        initiatingPurpose = searched.initiatingPurpose,
        currentPurpose = currentPurposeOrNull(session),
        initiatingClientId = searched.initiatingClientId,
        signedUp = searched.signedUp,
        user = searched.userId?.let(users::get),
        securityContext = searched.securityContext,
        sessionDate = searched.sessionDate,
        expirationDate = searched.expirationDate
    )

    /**
     * A session as a search reads it: what the filters, the text search and the order run over.
     *
     * Every field is read off the row rather than off the sealed model, and the model is not carried here at
     * all. Partly because the model drops some of them on some of its states — a failed session carries
     * neither its date, nor its user, nor how far it got — and those are the sessions this listing exists
     * for; and partly because nothing the criteria drop should cost anything more than the row it was read
     * from.
     */
    internal data class SearchedInteractiveFlowSession(
        val id: UUID,
        val status: InteractiveFlowSessionStatus,
        val initiatingPurpose: InteractiveFlowPurpose,
        val initiatingClientId: String?,
        val sessionDate: LocalDateTime,
        val expirationDate: LocalDateTime,
        val userId: UUID?,
        val signedUp: Boolean,
        val securityContext: InteractiveFlowSessionSecurityContext?
    )

    /**
     * The account a session belongs to, named rather than merely identified.
     *
     * [identifierClaims] is what this deployment identifies a person by and nothing else — an operator
     * debugging a session needs to know whose it is, and the rest of the claim set is what
     * `admin:users:read` is for.
     */
    data class InteractiveFlowSessionUser(
        val user: User,
        val identifierClaims: List<CollectedClaim>
    )

    /**
     * One row of the session listing.
     *
     * [user] is absent where the session has not identified anybody **and** where the account it identified
     * is one this very session is still signing up. [signedUp] is what tells those two apart, and it is why
     * it is on the row.
     *
     * [securityContext] is absent for every session outside the window in which an observation is staged —
     * see [InteractiveFlowSessionSecurityContext].
     */
    data class InteractiveFlowSessionSummary(
        val id: UUID,
        val status: InteractiveFlowSessionStatus,
        val initiatingPurpose: InteractiveFlowPurpose,
        val currentPurpose: InteractiveFlowPurpose?,
        val initiatingClientId: String?,
        val signedUp: Boolean,
        val user: InteractiveFlowSessionUser?,
        val securityContext: InteractiveFlowSessionSecurityContext?,
        val sessionDate: LocalDateTime,
        val expirationDate: LocalDateTime
    )

    /**
     * One session, every purpose it carries and where each one stands.
     *
     * The three error fields are the keys the session failed with and the values they interpolate, carried
     * unrendered: they are what a reader can grep for, whereas a sentence in the wrong locale says less. They
     * are absent for every status but [InteractiveFlowSessionStatus.FAILED].
     */
    data class InteractiveFlowSessionDetail(
        val id: UUID,
        val status: InteractiveFlowSessionStatus,
        val initiatingPurpose: InteractiveFlowPurpose,
        val initiatingClientId: String?,
        val flowId: String?,
        val user: InteractiveFlowSessionUser?,
        val signedUp: Boolean,
        val sessionDate: LocalDateTime,
        val expirationDate: LocalDateTime,
        val securityContext: InteractiveFlowSessionSecurityContext?,
        val errorDetailsId: String?,
        val errorDescriptionId: String?,
        val errorValues: Map<String, String>?,
        val purposes: List<InteractiveFlowPurposeProgress>
    )
}
