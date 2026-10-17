package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.Grant
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class AuthenticationTokenMapperTest {

    private val mapper = AuthenticationTokenMapper()

    private val grantId = UUID.randomUUID()

    private fun grant(id: UUID = grantId) = Grant(
        id = id,
        userId = null,
        clientId = "client",
        audienceId = "audience",
        grantType = GrantType.CLIENT_CREDENTIALS,
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
        actorGrantId = null,
        creationDate = LocalDateTime.now().minusMinutes(1),
        expirationDate = null,
        revokedAt = null,
        revokedBy = null,
        revokedById = null
    )

    @Test
    fun `toToken - maps the decoded columns with the grant the token was issued under`() {
        val id = UUID.randomUUID()
        val grant = grant()
        val entity = entity(
            id = id,
            type = AuthenticationTokenType.REFRESH.name,
            grantType = "refresh_token",
            dpopJkt = "jkt"
        )

        val token = mapper.toToken(entity, grant)

        assertEquals(id, token.id)
        assertEquals(AuthenticationTokenType.REFRESH, token.type)
        assertSame(grant, token.grant)
        assertEquals(GrantType.REFRESH_TOKEN, token.grantType)
        assertEquals("jkt", token.dpopJkt)
        assertEquals(entity.expirationDate, token.expirationDate)
    }

    @Test
    fun `toToken - maps the grant type a token exchange is published under`() {
        val token = mapper.toToken(
            entity(grantType = "urn:ietf:params:oauth:grant-type:token-exchange"),
            grant()
        )

        assertEquals(GrantType.TOKEN_EXCHANGE, token.grantType)
    }

    @Test
    fun `toToken - throws when type is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toToken(entity(type = "UNKNOWN"), grant())
        }
        assertInvalidProperty(exception, "type")
    }

    @Test
    fun `toToken - throws when grantType is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toToken(entity(grantType = "UNKNOWN"), grant())
        }
        assertInvalidProperty(exception, "grantType")
    }

    @Test
    fun `toToken - throws when the grant the row names is gone`() {
        val exception = assertThrows<BusinessException> {
            mapper.toToken(entity(), null)
        }
        assertInvalidProperty(exception, "grantId")
    }

    @Test
    fun `toToken - throws when handed another grant than the one the row names`() {
        val exception = assertThrows<BusinessException> {
            mapper.toToken(entity(), grant(UUID.randomUUID()))
        }
        assertInvalidProperty(exception, "grantId")
    }

    private fun assertInvalidProperty(exception: BusinessException, property: String) {
        assertEquals("mapper.authentication_token.invalid_property", exception.detailsId)
        assertEquals(mapOf("property" to property), exception.values)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        type: String = AuthenticationTokenType.ACCESS.name,
        grantType: String = "authorization_code",
        dpopJkt: String? = null
    ): AuthenticationTokenEntity {
        return AuthenticationTokenEntity(
            type = type,
            grantId = grantId,
            grantType = grantType,
            dpopJkt = dpopJkt,
            issueDate = LocalDateTime.now().minusMinutes(1),
            expirationDate = LocalDateTime.now().plusHours(1),
        ).apply { this.id = id }
    }
}
