package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMapper
import com.sympauthy.business.mapper.InteractiveFlowSessionSecurityContextMapper
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStatus
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.SortOrder
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.model.InteractiveFlowSessionSecurityContextEntity
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionSearchManagerTest {

    companion object {
        private val NOW: LocalDateTime = LocalDateTime.now()
    }

    @MockK
    lateinit var sessionRepository: InteractiveFlowSessionRepository

    @MockK
    lateinit var securityContextRepository: InteractiveFlowSessionSecurityContextRepository

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var purposeRegistry: InteractiveFlowPurposeRegistry

    private val sessionMapper: InteractiveFlowSessionMapper =
        Mappers.getMapper(InteractiveFlowSessionMapper::class.java)

    private val securityContextMapper: InteractiveFlowSessionSecurityContextMapper =
        Mappers.getMapper(InteractiveFlowSessionSecurityContextMapper::class.java)

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionSearchManager

    private val handedToHandlers = mutableListOf<InteractiveFlowSession>()

    @Test
    fun `listSessions - Reports a session that completed and has since expired as completed`() = runTest {
        givenSessions(
            entity(
                userId = UUID.randomUUID(),
                completeDate = NOW.minusMinutes(40),
                expirationDate = NOW.minusMinutes(10)
            )
        )
        givenNoObservation()
        givenNoUser()

        val page = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20))

        assertEquals(InteractiveFlowSessionStatus.COMPLETED, page.items.single().status)
    }

    @Test
    fun `listSessions - Reports a session that was ongoing at its expiration as expired`() = runTest {
        givenSessions(entity(expirationDate = NOW.minusMinutes(10)))
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(any()) } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE

        val page = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20))

        assertEquals(InteractiveFlowSessionStatus.EXPIRED, page.items.single().status)
    }

    @Test
    fun `listSessions - Answers which purpose an expired session stalled on`() = runTest {
        givenSessions(entity(expirationDate = NOW.minusMinutes(10)))
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(any()) } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE

        val page = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20))

        assertEquals(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, page.items.single().currentPurpose)
    }

    @Test
    fun `listSessions - Walks no purpose for a terminal session`() = runTest {
        // engine is left unstubbed: reaching the assertion proves the walk was never run.
        givenSessions(entity(cancelDate = NOW.minusMinutes(1), redirectType = "PLAIN"))
        givenNoObservation()

        val page = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20))

        assertNull(page.items.single().currentPurpose)
    }

    @Test
    fun `listSessions - Reports no current purpose for a row whose walk refuses to answer`() = runTest {
        // A session whose client the configuration has since dropped, or whose attached record is gone: the
        // engine refuses to advance it, and one such row must not take down the page the others are on.
        assertOneRefusedWalkLeavesThePageStanding(businessExceptionOf("client.invalid_client_id"))
    }

    @Test
    fun `listSessions - Reports no current purpose when the walk reaches a configuration that will not parse`() =
        runTest {
            // Not a BusinessException, and not sharing a supertype with one: the walk reads the clients and
            // the claims through the configuration, and enumerating what it can throw is what gets this wrong.
            assertOneRefusedWalkLeavesThePageStanding(configExceptionOf("clients", "config.clients.invalid"))
        }

    private suspend fun assertOneRefusedWalkLeavesThePageStanding(failure: Throwable) {
        val refused = entity(sessionDate = NOW.minusMinutes(5))
        val walkable = entity(sessionDate = NOW.minusMinutes(1))
        givenSessions(refused, walkable)
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(match { it.id == refused.id }) } throws failure
        coEvery { engine.currentPurposeOrNull(match { it.id == walkable.id }) } returns
            InteractiveFlowPurpose.OAUTH2_AUTHORIZE

        val page = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20))

        assertEquals(listOf(refused.id, walkable.id), page.items.map { it.id })
        assertNull(page.items.first().currentPurpose)
        assertEquals(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, page.items.last().currentPurpose)
    }

    @Test
    fun `listSessions - Lets a cancellation travel on rather than reporting no current purpose`() = runTest {
        givenSessions(entity())
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(any()) } throws CancellationException("the caller went away")

        assertThrows<CancellationException> {
            manager.listSessions(null, null, null, null, null, null, PageParams(0, 20))
        }
    }

    @Test
    fun `listSessions - Builds the sealed session for the page and not for everything the criteria kept`() =
        runTest {
            // The off-page row carries a purpose the enum does not name, so mapping it would throw. Reaching
            // the assertion is proof that only the page's row was mapped.
            val onPage = entity(sessionDate = NOW.minusMinutes(5))
            val offPage = entity(sessionDate = NOW.minusMinutes(1), purposes = arrayOf("NOT_A_PURPOSE"))
            givenSessions(onPage, offPage)
            givenNoObservation()
            coEvery { engine.currentPurposeOrNull(any()) } returns null

            val page = manager.listSessions(null, null, null, null, null, null, PageParams(0, 1))

            assertEquals(listOf(onPage.id), page.items.map { it.id })
        }

    @Test
    fun `listSessions - Matches q against the observed address`() = runTest {
        val matching = entity(sessionDate = NOW.minusMinutes(3))
        val other = entity(sessionDate = NOW.minusMinutes(2))
        givenSessions(matching, other)
        givenObservations(observation(matching.id!!, ip = "203.0.113.9"))
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val page = manager.listSessions("113.", null, null, null, null, null, PageParams(0, 20))

        assertEquals(listOf(matching.id), page.items.map { it.id })
    }

    @Test
    fun `listSessions - Matches q against the observed user agent, ignoring case`() = runTest {
        val matching = entity()
        givenSessions(matching, entity())
        givenObservations(observation(matching.id!!, userAgent = "Mozilla/5.0 Mobile Safari"))
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val page = manager.listSessions("mobile safari", null, null, null, null, null, PageParams(0, 20))

        assertEquals(listOf(matching.id), page.items.map { it.id })
    }

    @Test
    fun `listSessions - Matches q against the initiating client`() = runTest {
        val matching = entity(initiatingClientId = "web-app")
        givenSessions(matching, entity(initiatingClientId = "mobile-app"))
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val page = manager.listSessions("web", null, null, null, null, null, PageParams(0, 20))

        assertEquals(listOf(matching.id), page.items.map { it.id })
    }

    @Test
    fun `listSessions - Keeps only the sessions the status names`() = runTest {
        // engine is left unstubbed: the ongoing session is dropped by the filter, so the walk never runs.
        val cancelled = entity(cancelDate = NOW.minusMinutes(1), redirectType = "PLAIN")
        givenSessions(cancelled, entity())
        givenNoObservation()

        val page = manager.listSessions(
            null, null, null, null, InteractiveFlowSessionStatus.CANCELLED, null, PageParams(0, 20)
        )

        assertEquals(listOf(cancelled.id), page.items.map { it.id })
    }

    @Test
    fun `listSessions - Keeps only the sessions the client names`() = runTest {
        val matching = entity(initiatingClientId = "web-app")
        givenSessions(matching, entity(initiatingClientId = null))
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val page = manager.listSessions(null, "web-app", null, null, null, null, PageParams(0, 20))

        assertEquals(listOf(matching.id), page.items.map { it.id })
    }

    @Test
    fun `listSessions - Orders by the date the session started, oldest first`() = runTest {
        val older = entity(sessionDate = NOW.minusMinutes(5))
        val newer = entity(sessionDate = NOW.minusMinutes(1))
        givenSessions(newer, older)
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val page = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20))

        assertEquals(listOf(older.id, newer.id), page.items.map { it.id })
    }

    @Test
    fun `listSessions - Reverses the date under desc and leaves the tiebreak ascending`() = runTest {
        val sameDate = NOW.minusMinutes(5)
        val first = entity(id = UUID.fromString("00000000-0000-0000-0000-000000000001"), sessionDate = sameDate)
        val second = entity(id = UUID.fromString("00000000-0000-0000-0000-000000000002"), sessionDate = sameDate)
        val newer = entity(sessionDate = NOW.minusMinutes(1))
        givenSessions(first, second, newer)
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val page = manager.listSessions(null, null, null, null, null, SortOrder.DESC, PageParams(0, 20))

        assertEquals(listOf(newer.id, first.id, second.id), page.items.map { it.id })
    }

    @Test
    fun `listSessions - Reads the users of the published page and not of everything the criteria kept`() =
        runTest {
            val onPage = entity(userId = UUID.randomUUID(), sessionDate = NOW.minusMinutes(5))
            val offPage = entity(userId = UUID.randomUUID(), sessionDate = NOW.minusMinutes(1))
            givenSessions(onPage, offPage)
            givenNoObservation()
            givenNoUser()
            coEvery { engine.currentPurposeOrNull(any()) } returns null

            manager.listSessions(null, null, null, null, null, null, PageParams(0, 1))

            coVerify(exactly = 1) { userManager.listByIds(listOf(onPage.userId!!)) }
        }

    @Test
    fun `listSessions - Publishes no user for a session still signing an account up`() = runTest {
        val provisionalUserId = UUID.randomUUID()
        givenSessions(entity(userId = provisionalUserId, signedUp = true))
        givenNoObservation()
        // The account is provisional, so the committed-rows-only read answers with nothing.
        coEvery { userManager.listByIds(listOf(provisionalUserId)) } returns emptyList()
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val row = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20)).items.single()

        assertNull(row.user)
        assertTrue(row.signedUp)
    }

    @Test
    fun `listSessions - Names the account with its identifier claims`() = runTest {
        val userId = UUID.randomUUID()
        givenSessions(entity(userId = userId))
        givenNoObservation()
        coEvery { engine.currentPurposeOrNull(any()) } returns null
        givenUser(userId, "someone@example.com")

        val row = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20)).items.single()

        assertEquals(userId, row.user?.user?.id)
        assertEquals(listOf("someone@example.com"), row.user?.identifierClaims?.map { it.value })
    }

    @Test
    fun `listSessions - Publishes the observed address and user agent`() = runTest {
        val session = entity()
        givenSessions(session)
        givenObservations(observation(session.id!!, ip = "203.0.113.9", userAgent = "Mozilla/5.0"))
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val row = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20)).items.single()

        assertEquals("203.0.113.9", row.securityContext?.ip)
        assertEquals("Mozilla/5.0", row.securityContext?.userAgent)
    }

    /** A session driven from two places is one row, and the row says where it was last driven from. */
    @Test
    fun `listSessions - Publishes the place the session was last driven from`() = runTest {
        val session = entity()
        givenSessions(session)
        givenObservations(
            observation(session.id!!, ip = "203.0.113.9", lastSeenDate = NOW.minusMinutes(5)),
            observation(session.id!!, ip = "198.51.100.4", lastSeenDate = NOW)
        )
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val row = manager.listSessions(null, null, null, null, null, null, PageParams(0, 20)).items.single()

        assertEquals("198.51.100.4", row.securityContext?.ip)
    }

    /** An operator searching for an address finds the session it appeared in, latest place or not. */
    @Test
    fun `listSessions - Matches q against every place the session was driven from`() = runTest {
        val matching = entity()
        givenSessions(matching, entity())
        givenObservations(
            observation(matching.id!!, ip = "203.0.113.9", lastSeenDate = NOW.minusMinutes(5)),
            observation(matching.id!!, ip = "198.51.100.4", lastSeenDate = NOW)
        )
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val page = manager.listSessions("203.0.113.9", null, null, null, null, null, PageParams(0, 20))

        assertEquals(listOf(matching.id), page.items.map { it.id })
    }

    @Test
    fun `findSessionOrNull - Returns null when no session holds the identifier`() = runTest {
        val id = UUID.randomUUID()
        coEvery { sessionRepository.findById(id) } returns null

        assertNull(manager.findSessionOrNull(id))
    }

    @Test
    fun `findSessionOrNull - Marks each purpose completed, current or pending`() = runTest {
        val session = entity(
            purposes = arrayOf("CONFIRM", "REAUTHENTICATION", "LINK_PROVIDER"),
            completedPurposes = arrayOf("CONFIRM"),
            initiatingPurpose = "LINK_PROVIDER"
        )
        givenDetailReads(session)
        coEvery { engine.currentPurposeOrNull(any()) } returns InteractiveFlowPurpose.REAUTHENTICATION

        val detail = manager.findSessionOrNull(session.id!!)

        assertEquals(
            listOf(
                InteractiveFlowPurposeStatus.COMPLETED,
                InteractiveFlowPurposeStatus.CURRENT,
                InteractiveFlowPurposeStatus.PENDING
            ),
            detail?.purposes?.map { it.status }
        )
    }

    /** The trail an operator reading a stalled flow is after: oldest place first, not newest. */
    @Test
    fun `findSessionOrNull - Answers every place the session was driven from, oldest first`() = runTest {
        val session = entity()
        givenDetailReads(session)
        coEvery { securityContextRepository.findBySessionId(session.id!!) } returns listOf(
            observation(session.id!!, ip = "198.51.100.4", lastSeenDate = NOW),
            observation(
                session.id!!,
                ip = "203.0.113.9",
                firstSeenDate = NOW.minusMinutes(9),
                lastSeenDate = NOW.minusMinutes(5)
            )
        )
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        val detail = manager.findSessionOrNull(session.id!!)

        assertEquals(listOf("203.0.113.9", "198.51.100.4"), detail?.securityContexts?.map { it.ip })
    }

    @Test
    fun `findSessionOrNull - Reads the completed purposes of a failed session from its row`() = runTest {
        val session = entity(
            purposes = arrayOf("CONFIRM", "MFA_ENROLLMENT"),
            completedPurposes = arrayOf("CONFIRM"),
            initiatingPurpose = "MFA_ENROLLMENT",
            errorDate = NOW.minusMinutes(1),
            errorDetailsId = "some.error"
        )
        givenDetailReads(session)

        val detail = manager.findSessionOrNull(session.id!!)

        assertEquals(InteractiveFlowSessionStatus.FAILED, detail?.status)
        assertEquals(
            listOf(InteractiveFlowPurposeStatus.COMPLETED, InteractiveFlowPurposeStatus.PENDING),
            detail?.purposes?.map { it.status }
        )
        assertEquals("some.error", detail?.errorDetailsId)
    }

    @Test
    fun `findSessionOrNull - Asks each purpose's handler what it has to say`() = runTest {
        val session = entity(purposes = arrayOf("CONFIRM", "MFA_ENROLLMENT"), initiatingPurpose = "MFA_ENROLLMENT")
        givenDetailReads(session)
        coEvery { engine.currentPurposeOrNull(any()) } returns InteractiveFlowPurpose.CONFIRM

        val detail = manager.findSessionOrNull(session.id!!)

        assertEquals(
            listOf(
                listOf(PurposeDebugInformation("Said by CONFIRM", "yes")),
                listOf(PurposeDebugInformation("Said by MFA_ENROLLMENT", "yes"))
            ),
            detail?.purposes?.map { it.debugInformation }
        )
    }

    @Test
    fun `findSessionOrNull - Hands an expired session to the handlers as an ongoing one`() = runTest {
        val session = entity(userId = UUID.randomUUID(), expirationDate = NOW.minusMinutes(10))
        givenDetailReads(session)
        givenNoUser()
        coEvery { engine.currentPurposeOrNull(any()) } returns null

        manager.findSessionOrNull(session.id!!)

        val handed = handedToHandlers.single()
        assertTrue(handed is OnGoingInteractiveFlowSession, "an expired session is walked as an ongoing one")
        assertEquals(session.userId, (handed as OnGoingInteractiveFlowSession).userId)
    }

    private fun givenSessions(vararg entities: InteractiveFlowSessionEntity) {
        coEvery { sessionRepository.findAll() } returns flowOf(*entities)
    }

    private fun givenNoObservation() {
        coEvery { securityContextRepository.findAll() } returns flowOf()
    }

    private fun givenObservations(vararg entities: InteractiveFlowSessionSecurityContextEntity) {
        coEvery { securityContextRepository.findAll() } returns flowOf(*entities)
    }

    private fun givenNoUser() {
        coEvery { userManager.listByIds(any()) } returns emptyList()
    }

    private fun givenUser(userId: UUID, email: String) {
        val user = User(id = userId, status = UserStatus.ENABLED, creationDate = NOW, sessionId = null)
        coEvery { userManager.listByIds(listOf(userId)) } returns listOf(user)
        coEvery { collectedClaimManager.listIdentifierByUserIds(listOf(userId)) } returns listOf(
            CollectedClaim(
                userId = userId,
                claim = mockk<Claim>(),
                value = email,
                verified = true,
                collectionDate = NOW,
                verificationDate = NOW
            )
        )
    }

    private fun givenDetailReads(session: InteractiveFlowSessionEntity) {
        coEvery { sessionRepository.findById(session.id!!) } returns session
        coEvery { securityContextRepository.findBySessionId(session.id!!) } returns emptyList()
        session.purposes.forEach { purpose ->
            val handler = mockk<InteractiveFlowPurposeHandler>()
            coEvery { handler.debugInformation(capture(handedToHandlers)) } returns
                listOf(PurposeDebugInformation("Said by $purpose", "yes"))
            every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.valueOf(purpose)) } returns handler
        }
    }

    private fun observation(
        sessionId: UUID,
        ip: String = "127.0.0.1",
        userAgent: String? = null,
        firstSeenDate: LocalDateTime = NOW.minusMinutes(1),
        lastSeenDate: LocalDateTime = NOW,
        provenDate: LocalDateTime? = null
    ) = InteractiveFlowSessionSecurityContextEntity(
        sessionId = sessionId,
        fingerprint = "fingerprint-$ip-$userAgent",
        ip = ip,
        userAgent = userAgent,
        firstSeenDate = firstSeenDate,
        lastSeenDate = lastSeenDate,
        provenDate = provenDate
    )

    private fun entity(
        id: UUID = UUID.randomUUID(),
        purposes: Array<String> = arrayOf("OAUTH2_AUTHORIZE"),
        completedPurposes: Array<String> = emptyArray(),
        initiatingPurpose: String = "OAUTH2_AUTHORIZE",
        initiatingClientId: String? = null,
        sessionDate: LocalDateTime = NOW.minusMinutes(2),
        expirationDate: LocalDateTime = NOW.plusMinutes(10),
        userId: UUID? = null,
        signedUp: Boolean = false,
        redirectType: String? = null,
        completeDate: LocalDateTime? = null,
        cancelDate: LocalDateTime? = null,
        errorDate: LocalDateTime? = null,
        errorDetailsId: String? = null,
    ) = InteractiveFlowSessionEntity(
        purposes = purposes,
        completedPurposes = completedPurposes,
        initiatingPurpose = initiatingPurpose,
        initiatingClientId = initiatingClientId,
        sessionDate = sessionDate,
        flowId = "flow",
        expirationDate = expirationDate,
        userId = userId,
        signedUp = signedUp,
        successRedirectUri = completeDate?.let { "https://client.example.com/callback" },
        redirectType = redirectType ?: completeDate?.let { "AUTHORIZATION_CODE" },
        completeDate = completeDate,
        cancelDate = cancelDate,
        errorDate = errorDate,
        errorDetailsId = errorDetailsId,
    ).apply { this.id = id }
}
