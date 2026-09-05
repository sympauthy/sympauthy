package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.oauth2.TokenRevokedBy
import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class AuthenticationTokenMapperTest {

    private val mapper = Mappers.getMapper(AuthenticationTokenMapper::class.java)

    @Test
    fun `toToken - maps the decoded columns`() {
        val id = UUID.randomUUID()
        val entity = entity(
            id = id,
            type = AuthenticationTokenType.REFRESH.name,
            consentedBy = ConsentedBy.USER.name,
            grantedBy = GrantedBy.RULE.name,
            revokedBy = TokenRevokedBy.ADMIN.name,
        )

        val token = mapper.toToken(entity)

        assertEquals(id, token.id)
        assertEquals(AuthenticationTokenType.REFRESH, token.type)
        assertEquals(ConsentedBy.USER, token.consentedBy)
        assertEquals(GrantedBy.RULE, token.grantedBy)
        assertEquals(TokenRevokedBy.ADMIN, token.revokedBy)
    }

    @Test
    fun `toToken - maps a token that was never consented, granted or revoked`() {
        val token = mapper.toToken(entity())

        assertNull(token.consentedBy)
        assertNull(token.grantedBy)
        assertNull(token.revokedBy)
    }

    @Test
    fun `toToken - throws when type is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toToken(entity(type = "UNKNOWN"))
        }
        assertInvalidProperty(exception)
    }

    @Test
    fun `toToken - throws when revokedBy is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toToken(entity(revokedBy = "UNKNOWN"))
        }
        assertInvalidProperty(exception)
    }

    @Test
    fun `toToken - throws when consentedBy is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toToken(entity(consentedBy = "UNKNOWN"))
        }
        assertInvalidProperty(exception)
    }

    @Test
    fun `toToken - throws when grantedBy is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toToken(entity(grantedBy = "UNKNOWN"))
        }
        assertInvalidProperty(exception)
    }

    /**
     * A column that does not read back is refused under the mapper's own code, and not under one of the
     * coherence codes, which name a row whose columns each read back but contradict each other.
     */
    private fun assertInvalidProperty(exception: BusinessException) {
        assertEquals("mapper.authentication_token.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        type: String = AuthenticationTokenType.ACCESS.name,
        consentedBy: String? = null,
        grantedBy: String? = null,
        revokedBy: String? = null,
    ): AuthenticationTokenEntity {
        return AuthenticationTokenEntity(
            type = type,
            userId = UUID.randomUUID(),
            clientId = "client",
            grantedScopes = emptyArray(),
            grantedBy = grantedBy,
            consentedScopes = emptyArray(),
            consentedBy = consentedBy,
            clientScopes = emptyArray(),
            sessionId = UUID.randomUUID(),
            grantType = "authorization_code",
            revokedBy = revokedBy,
            issueDate = LocalDateTime.now().minusMinutes(1),
            expirationDate = LocalDateTime.now().plusHours(1),
        ).apply { this.id = id }
    }
}
