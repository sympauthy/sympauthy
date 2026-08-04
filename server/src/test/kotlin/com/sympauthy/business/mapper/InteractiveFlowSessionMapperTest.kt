package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.net.URI
import java.time.LocalDateTime
import java.util.*

class InteractiveFlowSessionMapperTest {

    private val mapper = Mappers.getMapper(InteractiveFlowSessionMapper::class.java)

    @Test
    fun `toInteractiveFlowSession - maps to Failed when errorDate is not null`() {
        val id = UUID.randomUUID()
        val entity = entity(
            id = id,
            errorDate = LocalDateTime.now().minusMinutes(1),
            errorDetailsId = "some.error",
            errorDescriptionId = "some.description",
            errorValues = mapOf("key" to "value"),
        )

        val session = mapper.toInteractiveFlowSession(entity)

        assertTrue(session is FailedInteractiveFlowSession)
        session as FailedInteractiveFlowSession
        assertEquals(id, session.id)
        assertEquals("some.error", session.errorDetailsId)
        assertEquals("some.description", session.errorDescriptionId)
        assertEquals(mapOf("key" to "value"), session.errorValues)
    }

    @Test
    fun `toInteractiveFlowSession - maps to synthesized expired Failed when session has expired`() {
        val id = UUID.randomUUID()
        val expirationDate = LocalDateTime.now().minusMinutes(1)
        val entity = entity(
            id = id,
            expirationDate = expirationDate,
            // No error and no completion: the expiration must take precedence.
            errorDate = null,
            completeDate = null,
        )

        val session = mapper.toInteractiveFlowSession(entity)

        assertTrue(session is FailedInteractiveFlowSession)
        session as FailedInteractiveFlowSession
        assertEquals(id, session.id)
        assertEquals("auth.interactive_flow_session.validate.expired", session.errorDetailsId)
        assertEquals("description.oauth2.expired", session.errorDescriptionId)
        assertEquals(expirationDate, session.errorDate)
    }

    @Test
    fun `toInteractiveFlowSession - maps to Completed when completeDate is not null`() {
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val completeDate = LocalDateTime.now()
        val entity = entity(
            id = id,
            userId = userId,
            completeDate = completeDate,
            successRedirectUri = "https://client.example.com/callback",
            redirectType = InteractiveFlowRedirectType.AUTHORIZATION_CODE.name,
        )

        val session = mapper.toInteractiveFlowSession(entity)

        assertTrue(session is CompletedInteractiveFlowSession)
        session as CompletedInteractiveFlowSession
        assertEquals(id, session.id)
        assertEquals(userId, session.userId)
        assertEquals(completeDate, session.completeDate)
        assertEquals(URI.create("https://client.example.com/callback"), session.successRedirectUri)
        assertEquals(InteractiveFlowRedirectType.AUTHORIZATION_CODE, session.redirectType)
    }

    @Test
    fun `toInteractiveFlowSession - maps to Cancelled when cancelDate is not null`() {
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val cancelDate = LocalDateTime.now()
        val entity = entity(
            id = id,
            userId = userId,
            redirectType = InteractiveFlowRedirectType.PLAIN.name,
            cancelRedirectUri = "https://client.example.com/cancelled",
            cancelDate = cancelDate,
        )

        val session = mapper.toInteractiveFlowSession(entity)

        assertTrue(session is CancelledInteractiveFlowSession)
        session as CancelledInteractiveFlowSession
        assertEquals(id, session.id)
        assertEquals(userId, session.userId)
        assertEquals(cancelDate, session.cancelDate)
        assertEquals(InteractiveFlowRedirectType.PLAIN, session.redirectType)
        assertEquals(URI.create("https://client.example.com/cancelled"), session.cancelRedirectUri)
    }

    @Test
    fun `toInteractiveFlowSession - maps to OnGoing otherwise`() {
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val mfaPassedDate = LocalDateTime.now().minusMinutes(1)
        val entity = entity(
            id = id,
            userId = userId,
            mfaPassedDate = mfaPassedDate,
        )

        val session = mapper.toInteractiveFlowSession(entity)

        assertTrue(session is OnGoingInteractiveFlowSession)
        session as OnGoingInteractiveFlowSession
        assertEquals(id, session.id)
        assertEquals(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE), session.purposes)
        assertEquals(userId, session.userId)
        assertEquals(mfaPassedDate, session.mfaPassedDate)
    }

    @Test
    fun `toInteractiveFlowSession - throws when OnGoing entity has no id`() {
        val entity = entity(id = null)

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSession(entity)
        }
        assertEquals("mapper.interactive_flow_session.invalid_property", exception.detailsId)
    }

    @Test
    fun `toInteractiveFlowSession - throws when Completed entity has no userId`() {
        val entity = entity(
            id = UUID.randomUUID(),
            userId = null,
            completeDate = LocalDateTime.now(),
        )

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSession(entity)
        }
        assertEquals("mapper.interactive_flow_session.invalid_property", exception.detailsId)
    }

    @Test
    fun `toInteractiveFlowSession - throws when Failed entity has no errorDetailsId`() {
        val entity = entity(
            id = UUID.randomUUID(),
            errorDate = LocalDateTime.now().minusMinutes(1),
            errorDetailsId = null,
        )

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSession(entity)
        }
        assertEquals("mapper.interactive_flow_session.invalid_property", exception.detailsId)
    }

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        purpose: InteractiveFlowPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
        flowId: String? = "flow",
        sessionDate: LocalDateTime = LocalDateTime.now().minusMinutes(2),
        expirationDate: LocalDateTime = LocalDateTime.now().plusMinutes(10),
        userId: UUID? = null,
        mfaPassedDate: LocalDateTime? = null,
        successRedirectUri: String? = null,
        redirectType: String? = null,
        cancelRedirectUri: String? = null,
        completeDate: LocalDateTime? = null,
        cancelDate: LocalDateTime? = null,
        errorDate: LocalDateTime? = null,
        errorDetailsId: String? = null,
        errorDescriptionId: String? = null,
        errorValues: Map<String, String>? = null,
    ): InteractiveFlowSessionEntity {
        return InteractiveFlowSessionEntity(
            purposes = arrayOf(purpose.name),
            initiatingPurpose = purpose.name,
            sessionDate = sessionDate,
            flowId = flowId,
            expirationDate = expirationDate,
            userId = userId,
            mfaPassedDate = mfaPassedDate,
            successRedirectUri = successRedirectUri,
            redirectType = redirectType,
            cancelRedirectUri = cancelRedirectUri,
            completeDate = completeDate,
            cancelDate = cancelDate,
            errorDate = errorDate,
            errorDetailsId = errorDetailsId,
            errorDescriptionId = errorDescriptionId,
            errorValues = errorValues,
        ).apply { this.id = id }
    }
}
