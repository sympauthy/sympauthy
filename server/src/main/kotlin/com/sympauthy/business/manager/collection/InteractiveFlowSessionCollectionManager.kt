package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowPurposeRegistry
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.mapper.InteractiveFlowSessionMapper
import com.sympauthy.business.mapper.InteractiveFlowSessionSecurityContextMapper
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeProgress
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStatus
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionSecurityContext
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.BOOLEAN
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.NUMBER
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFieldType.TIMEZONE
import com.sympauthy.business.model.collection.CollectionFieldType.UUID as UUID_FIELD
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.collection.fieldValues
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import com.sympauthy.util.loggerForClass
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
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
 * **The collection is read in memory**, as every other admin collection is — see
 * [com.sympauthy.business.manager.collection.UserCollectionManager] for the reason. This set is bounded by the session
 * expiry window rather than by how long the deployment has been running, so it is smaller than the user table
 * the same approach already serves.
 *
 * **Reading it is four reads.** Every session and every place any of them was driven from, then —
 * once the order has decided which rows are published — the users of that page and their identifier claims.
 * A session the criteria kept but the page left off costs nothing beyond the rows it was read from.
 *
 * **What is published costs more, per row, and deliberately.** Answering which purpose a session is stopped
 * at is the engine's walk, and the walk asks every handler in turn — so a page of twenty costs twenty walks,
 * not one read. That is the question the endpoint exists to answer and it is not stored anywhere; keeping it
 * on the page rather than on everything the criteria kept is what bounds it.
 *
 * **It reads no attached record for the collection.** The client is a column on the session, so every criterion
 * matches against the session row and the places it was driven from — any of them, since a session that
 * moved mid-flow holds several and an operator searching for an address means whichever of them it appeared
 * in. The detail reads the attached records, one session at a time, through the handlers that own them, and
 * the places a session holds are a collection of their own rather than something the detail carries: they
 * are a trail an operator opens deliberately, and they are the one attached record with no handler.
 */
@Singleton
class InteractiveFlowSessionCollectionManager(
    @Inject private val sessionRepository: InteractiveFlowSessionRepository,
    @Inject private val securityContextRepository: InteractiveFlowSessionSecurityContextRepository,
    @Inject private val userManager: UserManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val sessionMapper: InteractiveFlowSessionMapper,
    @Inject private val securityContextMapper: InteractiveFlowSessionSecurityContextMapper,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val purposeRegistry: InteractiveFlowPurposeRegistry,
    @Inject private val clientManager: ClientManager
) {

    private val logger = loggerForClass()

    /**
     * What the session collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = sessionFields().capabilities

    /**
     * What the collection of the places one session was driven from accepts, which is what it publishes about
     * itself.
     */
    suspend fun securityContextCapabilities(): CollectionCapabilities = securityContextFields().capabilities

    /**
     * Read the page [pageParams] names of the sessions [criteria] keep.
     *
     * The order ends on the session's identifier, so it is total and a new session appends at the tail. That
     * identifier stays ascending whichever direction the caller asked for: it is not what they asked to sort
     * by, it is there to decide what their own keys leave undecided.
     */
    suspend fun listSessions(
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<InteractiveFlowSessionSummary> {
        val securityContexts = securityContextRepository.findAll().toList()
            .map(securityContextMapper::toInteractiveFlowSessionSecurityContext)
            .groupBy(InteractiveFlowSessionSecurityContext::sessionId)

        val entitiesById = sessionRepository.findAll().toList().associateBy(sessionMapper::toId)

        val rows = entitiesById.map { (id, entity) ->
            InteractiveFlowSessionRow(
                id = id,
                status = sessionMapper.toStatus(entity),
                initiatingPurpose = sessionMapper.toInitiatingPurpose(entity),
                initiatingClientId = entity.initiatingClientId,
                sessionDate = entity.sessionDate,
                expirationDate = entity.expirationDate,
                userId = entity.userId,
                signedUp = entity.signedUp,
                securityContexts = securityContexts[id].orEmpty()
            )
        }

        val page = sessionFields().page(rows, criteria, pageParams)
        val users = readUsersOf(page.items.mapNotNull(InteractiveFlowSessionRow::userId))
        // The sealed model is built for the page and not beside every row: it is read only to walk the
        // purposes of what is published, so a session the criteria kept but the order left off pays for
        // neither the mapping nor the walk — and a row that cannot be read back cannot take down a page it
        // does not appear on.
        return page.map { row ->
            val session = sessionMapper.toInteractiveFlowSessionAs(
                entitiesById.getValue(row.id),
                row.status
            )
            toSummary(row, session, users)
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
     * Read the page [pageParams] names of the places [sessionId] was driven from, or null where no session
     * holds that identifier — which, past the expiry window, is every session this server ever ran.
     *
     * **Most recently seen first where the caller named no order of their own**, because what a reader
     * opens a stalled session for is where it is being driven from now. That is a column every request
     * rewrites, so two calls agree on a snapshot and a walk in progress can see a place twice or skip one;
     * the endpoint's own description says so.
     */
    suspend fun listSecurityContexts(
        sessionId: UUID,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<InteractiveFlowSessionSecurityContext>? {
        sessionRepository.findById(sessionId) ?: return null
        val places = securityContextRepository.findBySessionId(sessionId)
            .map(securityContextMapper::toInteractiveFlowSessionSecurityContext)
        return securityContextFields().page(places, criteria, pageParams)
    }

    /**
     * The purpose driving [session], or null where none does — which is every terminal session, since the
     * engine's walk is over an ongoing one — and where the walk could not answer.
     *
     * A session that was ongoing when it expired is built as ongoing and does have one: which purpose it
     * stalled on is the question an operator opened it with.
     *
     * **The walk's failure is caught here, unlike a handler's refusal to describe a session.** The two look
     * alike and are not. `nextStepOrNull` answers to the engine, which drives a live flow where a missing
     * attached record, a client the configuration has dropped and a configuration that stopped parsing are
     * each a genuine failure that must stop the person — whereas this collection only asks it a question, about
     * somebody else's session, and one row the engine would refuse to advance must not take down the page
     * every other row is on. `debugInformation` was written for this endpoint and its contract is to answer,
     * so a failure there is a bug in something this surface owns and nothing catches around it.
     *
     * **Which is why what is caught is anything, rather than the failures anybody thought of.** The walk
     * reaches most of the server — every handler, the records they read, the claims and the configuration
     * behind them — and those throw out of more than one hierarchy: a `BusinessException` for a client the
     * configuration no longer declares, a `ConfigurationException` for a configuration that would not parse,
     * neither of which shares a supertype with the other. Enumerating them is how this was first written and
     * the second one was already missing. What is not caught is a cancellation, which says the request this
     * was answering for is gone, and the log is what keeps a swallowed failure from being invisible.
     */
    internal suspend fun currentPurposeOrNull(session: InteractiveFlowSession): InteractiveFlowPurpose? {
        val ongoing = session as? OnGoingInteractiveFlowSession ?: return null
        return try {
            engine.currentPurposeOrNull(ongoing)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            logger.warn("Could not tell which purpose session ${ongoing.id} is stopped at.", failure)
            null
        }
    }

    /**
     * The fields the session collection offers, ending on the session identifier, which is unique by
     * construction — and which is therefore not one a caller may order by.
     *
     * **`ip` and `user_agent` read every place the session was driven from**, so a criterion on either is
     * satisfied by any of them: an operator searching for an address finds the session it appeared in
     * whether or not it is the latest one.
     */
    private suspend fun sessionFields(): CollectionFields<InteractiveFlowSessionRow> = collectionFields(
        uniqueKey = compareBy(InteractiveFlowSessionRow::id)
    ) {
        field("id", UUID_FIELD, key = "fields.interactive_flow_session_id") { it.id }
        enumeration<InteractiveFlowSessionStatus>(
            name = "status",
            key = "fields.interactive_flow_session_status",
            sortable = true,
            read = InteractiveFlowSessionRow::status
        )
        enumeration<InteractiveFlowPurpose>(
            name = "purpose",
            key = "fields.interactive_flow_purpose",
            sortable = true,
            read = InteractiveFlowSessionRow::initiatingPurpose
        )
        field(
            name = "client",
            type = ENUM,
            key = "fields.client_id",
            values = fieldValues("fields.client_id", clientManager.listClients().map(Client::id)),
            nullable = true,
            sortable = true,
            searchable = true,
            read = InteractiveFlowSessionRow::initiatingClientId
        )
        field("user", UUID_FIELD, key = "fields.user_id", nullable = true) { it.userId }
        field("signed_up", BOOLEAN, sortable = true, read = InteractiveFlowSessionRow::signedUp)
        field("session_date", DATE_TIME, sortable = true, read = InteractiveFlowSessionRow::sessionDate)
        field(
            name = "expiration_date",
            type = DATE_TIME,
            sortable = true,
            read = InteractiveFlowSessionRow::expirationDate
        )
        field("ip", STRING, nullable = true, searchable = true) { row ->
            row.securityContexts.map(InteractiveFlowSessionSecurityContext::ip)
        }
        field("user_agent", STRING, nullable = true, searchable = true) { row ->
            row.securityContexts.mapNotNull(InteractiveFlowSessionSecurityContext::userAgent)
        }
        defaultSort("session_date")
    }

    /**
     * The fields the collection of one session's places offers, ending on the address and the user agent, which
     * are unique within a session by construction: they are what the fingerprint the places are deduplicated
     * on is computed from. Neither is therefore a key a caller may order by.
     */
    private suspend fun securityContextFields(): CollectionFields<InteractiveFlowSessionSecurityContext> =
        collectionFields(
            uniqueKey = compareBy<InteractiveFlowSessionSecurityContext> { it.ip }
                .thenBy(nullsLast()) { it.userAgent }
        ) {
            field("ip", STRING, searchable = true, read = InteractiveFlowSessionSecurityContext::ip)
            field(
                name = "user_agent",
                type = STRING,
                nullable = true,
                searchable = true,
                read = InteractiveFlowSessionSecurityContext::userAgent
            )
            field(
                name = "country_code",
                type = STRING,
                nullable = true,
                sortable = true,
                searchable = true,
                read = InteractiveFlowSessionSecurityContext::countryCode
            )
            field(
                name = "region",
                type = STRING,
                nullable = true,
                sortable = true,
                searchable = true,
                read = InteractiveFlowSessionSecurityContext::region
            )
            field(
                name = "city",
                type = STRING,
                nullable = true,
                sortable = true,
                searchable = true,
                read = InteractiveFlowSessionSecurityContext::city
            )
            field(
                name = "time_zone",
                type = TIMEZONE,
                nullable = true,
                sortable = true,
                read = InteractiveFlowSessionSecurityContext::timeZone
            )
            field(
                name = "first_seen_date",
                type = DATE_TIME,
                sortable = true,
                read = InteractiveFlowSessionSecurityContext::firstSeenDate
            )
            field(
                name = "last_seen_date",
                type = DATE_TIME,
                sortable = true,
                read = InteractiveFlowSessionSecurityContext::lastSeenDate
            )
            field(
                name = "observation_count",
                type = NUMBER,
                sortable = true,
                read = InteractiveFlowSessionSecurityContext::observationCount
            )
            field(
                name = "proven_date",
                type = DATE_TIME,
                nullable = true,
                sortable = true,
                read = InteractiveFlowSessionSecurityContext::provenDate
            )
            defaultSort("-last_seen_date")
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
     * The row [row] is published as, with the purpose driving it and the account it belongs to.
     *
     * Called on the sessions of one page rather than on everything the criteria kept: the walk that answers
     * the current purpose asks every handler, so a session the order left off costs none of those reads.
     */
    private suspend fun toSummary(
        row: InteractiveFlowSessionRow,
        session: InteractiveFlowSession,
        users: Map<UUID, InteractiveFlowSessionUser>
    ) = InteractiveFlowSessionSummary(
        id = row.id,
        status = row.status,
        initiatingPurpose = row.initiatingPurpose,
        currentPurpose = currentPurposeOrNull(session),
        initiatingClientId = row.initiatingClientId,
        signedUp = row.signedUp,
        user = row.userId?.let(users::get),
        securityContext = row.securityContexts.maxByOrNull(
            InteractiveFlowSessionSecurityContext::lastSeenDate
        ),
        sessionDate = row.sessionDate,
        expirationDate = row.expirationDate
    )

    /**
     * A session as this collection reads it: what the criteria, the free text and the order run over.
     *
     * Every field is read off the row rather than off the sealed model, and the model is not carried here at
     * all. Partly because the model drops some of them on some of its states — a failed session carries
     * neither its date, nor its user, nor how far it got — and those are the sessions this collection exists
     * for; and partly because nothing the criteria drop should cost anything more than the row it was read
     * from.
     */
    internal data class InteractiveFlowSessionRow(
        val id: UUID,
        val status: InteractiveFlowSessionStatus,
        val initiatingPurpose: InteractiveFlowPurpose,
        val initiatingClientId: String?,
        val sessionDate: LocalDateTime,
        val expirationDate: LocalDateTime,
        val userId: UUID?,
        val signedUp: Boolean,
        val securityContexts: List<InteractiveFlowSessionSecurityContext>
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
     * One row of the session collection.
     *
     * [user] is absent where the session has not identified anybody **and** where the account it identified
     * is one this very session is still signing up. [signedUp] is what tells those two apart, and it is why
     * it is on the row.
     *
     * [securityContext] is the place the session was last driven from, and it is absent only where nothing
     * was recorded against it at all. The rest of the trail is on the detail — see
     * [InteractiveFlowSessionSecurityContext].
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
        val errorDetailsId: String?,
        val errorDescriptionId: String?,
        val errorValues: Map<String, String>?,
        val purposes: List<InteractiveFlowPurposeProgress>
    )
}
