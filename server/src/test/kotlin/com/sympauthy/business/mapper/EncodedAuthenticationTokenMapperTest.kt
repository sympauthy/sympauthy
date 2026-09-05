package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class EncodedAuthenticationTokenMapperTest {

    private val mapper = Mappers.getMapper(EncodedAuthenticationTokenMapper::class.java)

    @Test
    fun `toEncodedAuthenticationToken - maps all fields`() {
        val id = UUID.randomUUID()
        val issueDate = LocalDateTime.now().minusMinutes(1)
        val expirationDate = LocalDateTime.now().plusHours(1)
        val entity = entity(
            id = id,
            grantedScopes = arrayOf("granted"),
            consentedScopes = arrayOf("consented"),
            clientScopes = arrayOf("client"),
            issueDate = issueDate,
            expirationDate = expirationDate,
        )

        val token = mapper.toEncodedAuthenticationToken(entity, "encoded")

        assertEquals(id, token.id)
        assertEquals(AuthenticationTokenType.ACCESS, token.type)
        assertEquals("encoded", token.token)
        assertEquals(listOf("granted", "consented", "client"), token.scopes)
        assertEquals(issueDate, token.issueDate)
        assertEquals(expirationDate, token.expirationDate)
    }

    @Test
    fun `toEncodedAuthenticationToken - throws when entity has no id`() {
        val entity = entity(id = null)

        val exception = assertThrows<BusinessException> {
            mapper.toEncodedAuthenticationToken(entity, "encoded")
        }
        assertEquals("mapper.encoded_authentication_token.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toEncodedAuthenticationToken - throws when type is unknown`() {
        val entity = entity(type = "UNKNOWN")

        val exception = assertThrows<BusinessException> {
            mapper.toEncodedAuthenticationToken(entity, "encoded")
        }
        assertEquals("mapper.encoded_authentication_token.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        type: String = AuthenticationTokenType.ACCESS.name,
        grantedScopes: Array<String> = emptyArray(),
        consentedScopes: Array<String> = emptyArray(),
        clientScopes: Array<String> = emptyArray(),
        issueDate: LocalDateTime = LocalDateTime.now().minusMinutes(1),
        expirationDate: LocalDateTime? = LocalDateTime.now().plusHours(1),
    ): AuthenticationTokenEntity {
        return AuthenticationTokenEntity(
            type = type,
            userId = UUID.randomUUID(),
            clientId = "client",
            grantedScopes = grantedScopes,
            consentedScopes = consentedScopes,
            clientScopes = clientScopes,
            sessionId = UUID.randomUUID(),
            grantType = "authorization_code",
            issueDate = issueDate,
            expirationDate = expirationDate,
        ).apply { this.id = id }
    }
}
