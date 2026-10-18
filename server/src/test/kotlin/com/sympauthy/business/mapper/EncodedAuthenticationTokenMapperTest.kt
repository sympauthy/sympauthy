package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class EncodedAuthenticationTokenMapperTest {

    private val mapper = EncodedAuthenticationTokenMapper()

    @Test
    fun `toEncodedAuthenticationToken - maps all fields`() {
        val id = UUID.randomUUID()
        val issueDate = LocalDateTime.now().minusMinutes(1)
        val expirationDate = LocalDateTime.now().plusHours(1)
        val entity = entity(
            id = id,
            issueDate = issueDate,
            expirationDate = expirationDate,
        )
        val grant = grant(clientScopes = listOf("client"))

        val token = mapper.toEncodedAuthenticationToken(entity, grant, "encoded")

        assertEquals(id, token.id)
        assertEquals(AuthenticationTokenType.ACCESS, token.type)
        assertEquals("encoded", token.token)
        assertEquals(listOf("client"), token.scopes)
        assertEquals(issueDate, token.issueDate)
        assertEquals(expirationDate, token.expirationDate)
    }

    @Test
    fun `toEncodedAuthenticationToken - throws when entity has no id`() {
        val entity = entity(id = null)

        val exception = assertThrows<BusinessException> {
            mapper.toEncodedAuthenticationToken(entity, grant(), "encoded")
        }
        assertEquals("mapper.encoded_authentication_token.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toEncodedAuthenticationToken - throws when type is unknown`() {
        val entity = entity(type = "UNKNOWN")

        val exception = assertThrows<BusinessException> {
            mapper.toEncodedAuthenticationToken(entity, grant(), "encoded")
        }
        assertEquals("mapper.encoded_authentication_token.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun grant(id: UUID = UUID.randomUUID(), clientScopes: List<String> = emptyList()) = Grant(
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
        clientScopes = clientScopes,
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

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        type: String = AuthenticationTokenType.ACCESS.name,
        issueDate: LocalDateTime = LocalDateTime.now().minusMinutes(1),
        expirationDate: LocalDateTime? = LocalDateTime.now().plusHours(1),
    ): AuthenticationTokenEntity {
        return AuthenticationTokenEntity(
            type = type,
            grantId = UUID.randomUUID(),
            grantType = "authorization_code",
            issueDate = issueDate,
            expirationDate = expirationDate,
        ).apply { this.id = id }
    }
}
