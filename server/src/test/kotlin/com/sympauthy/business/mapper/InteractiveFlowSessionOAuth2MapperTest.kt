package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.data.model.InteractiveFlowSessionOAuth2Entity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class InteractiveFlowSessionOAuth2MapperTest {

    private val mapper = Mappers.getMapper(InteractiveFlowSessionOAuth2Mapper::class.java)

    @Test
    fun `toInteractiveFlowSessionOAuth2 - maps all fields`() {
        val sessionId = UUID.randomUUID()
        val invitationId = UUID.randomUUID()
        val consentedAt = LocalDateTime.now().minusMinutes(2)
        val grantedAt = LocalDateTime.now().minusMinutes(1)
        val entity = InteractiveFlowSessionOAuth2Entity(
            sessionId = sessionId,
            clientId = "client",
            redirectUri = "https://client.test/callback",
            requestedScopes = arrayOf("openid", "profile"),
            state = "state",
            nonce = "nonce",
            codeChallenge = "challenge",
            codeChallengeMethod = CodeChallengeMethod.S256.value,
            invitationId = invitationId,
            consentedScopes = arrayOf("openid"),
            consentedAt = consentedAt,
            consentedBy = ConsentedBy.USER.name,
            grantedScopes = arrayOf("profile"),
            grantedAt = grantedAt,
            grantedBy = GrantedBy.RULE.name,
        )

        val oauth2 = mapper.toInteractiveFlowSessionOAuth2(entity)

        assertEquals(sessionId, oauth2.sessionId)
        assertEquals("client", oauth2.clientId)
        assertEquals("https://client.test/callback", oauth2.redirectUri)
        assertEquals(listOf("openid", "profile"), oauth2.requestedScopes)
        assertEquals("state", oauth2.state)
        assertEquals("nonce", oauth2.nonce)
        assertEquals("challenge", oauth2.codeChallenge)
        assertEquals(CodeChallengeMethod.S256, oauth2.codeChallengeMethod)
        assertEquals(invitationId, oauth2.invitationId)
        assertEquals(listOf("openid"), oauth2.consentedScopes)
        assertEquals(consentedAt, oauth2.consentedAt)
        assertEquals(ConsentedBy.USER, oauth2.consentedBy)
        assertEquals(listOf("profile"), oauth2.grantedScopes)
        assertEquals(grantedAt, oauth2.grantedAt)
        assertEquals(GrantedBy.RULE, oauth2.grantedBy)
    }

    @Test
    fun `toInteractiveFlowSessionOAuth2 - maps when both PKCE fields are null`() {
        val entity = entity(
            codeChallenge = null,
            codeChallengeMethod = null,
        )

        val oauth2 = mapper.toInteractiveFlowSessionOAuth2(entity)

        assertNull(oauth2.codeChallenge)
        assertNull(oauth2.codeChallengeMethod)
    }

    @Test
    fun `toInteractiveFlowSessionOAuth2 - throws when code challenge has no method`() {
        val entity = entity(
            codeChallenge = "challenge",
            codeChallengeMethod = null,
        )

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionOAuth2(entity)
        }
        assertEquals("mapper.interactive_flow_session_oauth2.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toInteractiveFlowSessionOAuth2 - throws when method has no code challenge`() {
        val entity = entity(
            codeChallenge = null,
            codeChallengeMethod = CodeChallengeMethod.S256.value,
        )

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionOAuth2(entity)
        }
        assertEquals("mapper.interactive_flow_session_oauth2.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toInteractiveFlowSessionOAuth2 - throws when method is unknown`() {
        val entity = entity(
            codeChallenge = "challenge",
            codeChallengeMethod = "plain",
        )

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionOAuth2(entity)
        }
        assertEquals("mapper.interactive_flow_session_oauth2.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toInteractiveFlowSessionOAuth2 - throws when clientId is null`() {
        val entity = entity(clientId = null)

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionOAuth2(entity)
        }
        assertEquals("mapper.interactive_flow_session_oauth2.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toInteractiveFlowSessionOAuth2 - throws when redirectUri is null`() {
        val entity = entity(redirectUri = null)

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionOAuth2(entity)
        }
        assertEquals("mapper.interactive_flow_session_oauth2.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toInteractiveFlowSessionOAuth2 - throws when consentedBy is unknown`() {
        val entity = entity(consentedBy = "UNKNOWN")

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionOAuth2(entity)
        }
        assertEquals("mapper.interactive_flow_session_oauth2.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toInteractiveFlowSessionOAuth2 - throws when grantedBy is unknown`() {
        val entity = entity(grantedBy = "UNKNOWN")

        val exception = assertThrows<BusinessException> {
            mapper.toInteractiveFlowSessionOAuth2(entity)
        }
        assertEquals("mapper.interactive_flow_session_oauth2.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun entity(
        sessionId: UUID = UUID.randomUUID(),
        consentedBy: String? = null,
        grantedBy: String? = null,
        clientId: String? = "client",
        redirectUri: String? = "https://client.test/callback",
        requestedScopes: Array<String> = arrayOf("openid"),
        codeChallenge: String? = null,
        codeChallengeMethod: String? = null,
    ): InteractiveFlowSessionOAuth2Entity {
        return InteractiveFlowSessionOAuth2Entity(
            sessionId = sessionId,
            clientId = clientId,
            redirectUri = redirectUri,
            requestedScopes = requestedScopes,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            consentedBy = consentedBy,
            grantedBy = grantedBy,
        )
    }
}
