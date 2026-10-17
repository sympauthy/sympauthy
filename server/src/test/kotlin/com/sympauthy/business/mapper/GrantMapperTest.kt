package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantRevokedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.data.model.GrantEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class GrantMapperTest {

    private val mapper = GrantMapper()

    @Test
    fun `toGrant - maps the decoded columns`() {
        val id = UUID.randomUUID()
        val grant = mapper.toGrant(
            entity(
                consentedBy = ConsentedBy.USER.name,
                grantedBy = GrantedBy.RULE.name,
                revokedBy = GrantRevokedBy.ADMIN.name
            ).apply { this.id = id }
        )

        assertEquals(id, grant.id)
        assertEquals(GrantType.AUTHORIZATION_CODE, grant.grantType)
        assertEquals(ConsentedBy.USER, grant.consentedBy)
        assertEquals(GrantedBy.RULE, grant.grantedBy)
        assertEquals(GrantRevokedBy.ADMIN, grant.revokedBy)
        assertTrue(grant.revoked)
    }

    @Test
    fun `toGrant - maps a grant that was never consented, granted or revoked`() {
        val grant = mapper.toGrant(entity())

        assertNull(grant.consentedBy)
        assertNull(grant.grantedBy)
        assertNull(grant.revokedBy)
        assertFalse(grant.revoked)
    }

    @Test
    fun `toGrant - maps the claims the request asked for, per channel`() {
        val grant = mapper.toGrant(
            entity(
                requestedIdTokenClaims = arrayOf("loyalty_tier"),
                requestedUserinfoClaims = arrayOf("shoe_size")
            )
        )

        assertEquals(setOf("loyalty_tier"), grant.requestedClaims.idTokenClaimIds)
        assertEquals(setOf("shoe_size"), grant.requestedClaims.userInfoClaimIds)
    }

    @Test
    fun `toGrant - maps a grant whose request asked for nothing`() {
        assertEquals(RequestedClaims.NONE, mapper.toGrant(entity()).requestedClaims)
    }

    @Test
    fun `toGrant - maps an act-as grant opened by a token exchange`() {
        val actorGrantId = UUID.randomUUID()
        val grant = mapper.toGrant(
            entity(
                grantType = "urn:ietf:params:oauth:grant-type:token-exchange",
                sessionId = null,
                authenticationDate = null,
                actorGrantId = actorGrantId
            )
        )

        assertEquals(GrantType.TOKEN_EXCHANGE, grant.grantType)
        assertEquals(actorGrantId, grant.actorGrantId)
    }

    @Test
    fun `toGrant - throws when grantType names no grant type`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(grantType = "UNKNOWN"))
        }
        assertInvalidProperty(exception, "grantType")
    }

    @Test
    fun `toGrant - throws when grantType names the refresh, which opens no grant`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(grantType = "refresh_token"))
        }
        assertInvalidProperty(exception, "grantType")
    }

    @Test
    fun `toGrant - throws when revokedBy is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(revokedBy = "UNKNOWN"))
        }
        assertInvalidProperty(exception, "revokedBy")
    }

    @Test
    fun `toGrant - throws when consentedBy is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(consentedBy = "UNKNOWN"))
        }
        assertInvalidProperty(exception, "consentedBy")
    }

    @Test
    fun `toGrant - throws when grantedBy is unknown`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(grantedBy = "UNKNOWN"))
        }
        assertInvalidProperty(exception, "grantedBy")
    }

    @Test
    fun `toGrant - throws for an authorization code grant naming no session`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(sessionId = null))
        }
        assertEquals("mapper.grant.missing_session_id", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toGrant - throws for a client credentials grant naming a person`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(grantType = "client_credentials", sessionId = null, authenticationDate = null))
        }
        assertEquals("mapper.grant.client_credentials.invalid_user_id", exception.detailsId)
    }

    @Test
    fun `toGrant - throws for a client credentials grant stating when somebody authenticated`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(grantType = "client_credentials", userId = null, sessionId = null))
        }
        assertEquals("mapper.grant.invalid_authentication_date", exception.detailsId)
        assertEquals(mapOf("grantType" to "client_credentials"), exception.values)
    }

    @Test
    fun `toGrant - throws for a token exchange grant naming no actor grant`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(
                entity(
                    grantType = "urn:ietf:params:oauth:grant-type:token-exchange",
                    sessionId = null,
                    authenticationDate = null
                )
            )
        }
        assertEquals("mapper.grant.token_exchange.missing_actor_grant_id", exception.detailsId)
    }

    @Test
    fun `toGrant - throws for an authorization code grant naming an actor grant`() {
        val exception = assertThrows<BusinessException> {
            mapper.toGrant(entity(actorGrantId = UUID.randomUUID()))
        }
        assertEquals("mapper.grant.invalid_actor_grant_id", exception.detailsId)
    }

    private fun assertInvalidProperty(exception: BusinessException, property: String) {
        assertEquals("mapper.grant.invalid_property", exception.detailsId)
        assertEquals(mapOf("property" to property), exception.values)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Suppress("LongParameterList")
    private fun entity(
        userId: UUID? = UUID.randomUUID(),
        grantType: String = "authorization_code",
        consentedBy: String? = null,
        grantedBy: String? = null,
        revokedBy: String? = null,
        requestedIdTokenClaims: Array<String> = emptyArray(),
        requestedUserinfoClaims: Array<String> = emptyArray(),
        sessionId: UUID? = UUID.randomUUID(),
        authenticationDate: LocalDateTime? = LocalDateTime.now().minusMinutes(5),
        actorGrantId: UUID? = null
    ): GrantEntity {
        return GrantEntity(
            userId = userId,
            clientId = "client",
            audienceId = "audience",
            grantType = grantType,
            grantedScopes = emptyArray(),
            grantedBy = grantedBy,
            consentedScopes = emptyArray(),
            consentedBy = consentedBy,
            clientScopes = emptyArray(),
            authenticationDate = authenticationDate,
            requestedIdTokenClaims = requestedIdTokenClaims,
            requestedUserinfoClaims = requestedUserinfoClaims,
            sessionId = sessionId,
            actorGrantId = actorGrantId,
            creationDate = LocalDateTime.now().minusMinutes(1),
            revokedAt = revokedBy?.let { LocalDateTime.now() },
            revokedBy = revokedBy
        ).apply { id = UUID.randomUUID() }
    }
}
