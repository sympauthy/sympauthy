package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.grant.GrantStatus
import com.sympauthy.business.model.grant.GrantWithStatus
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.user.claim.RequestedClaims
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class AdminGrantResourceMapperTest {

    private val mapper = AdminGrantResourceMapper()

    private val createdAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    private val revokerId: UUID = UUID.randomUUID()

    private fun grant(
        grantType: GrantType = GrantType.AUTHORIZATION_CODE,
        revokedBy: GrantRevokedBy? = null,
        requestedClaims: RequestedClaims = RequestedClaims.NONE
    ) = Grant(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        clientId = "web",
        audienceId = "default",
        grantType = grantType,
        grantedScopes = listOf("admin:grants:read"),
        grantedAt = createdAt,
        grantedBy = GrantedBy.RULE,
        consentedScopes = listOf("openid"),
        consentedAt = createdAt,
        consentedBy = ConsentedBy.USER,
        clientScopes = emptyList(),
        authenticationDate = createdAt.minusMinutes(1),
        requestedClaims = requestedClaims,
        sessionId = UUID.randomUUID(),
        actorGrantId = null,
        creationDate = createdAt,
        expirationDate = null,
        revokedAt = revokedBy?.let { createdAt.plusHours(1) },
        revokedBy = revokedBy,
        revokedById = revokedBy?.let { revokerId }
    )

    @Test
    fun `toSummaryResource - Publish the grant type and the status under the names they travel by`() {
        val resource = mapper.toSummaryResource(
            GrantWithStatus(grant(grantType = GrantType.TOKEN_EXCHANGE), GrantStatus.LAPSED)
        )

        assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", resource.grantType)
        assertEquals("lapsed", resource.status)
    }

    @Test
    fun `toDetailResource - Name the administrator who revoked the grant`() {
        val resource = mapper.toDetailResource(
            GrantWithStatus(grant(revokedBy = GrantRevokedBy.ADMIN), GrantStatus.REVOKED)
        )

        assertEquals("admin", resource.revokedBy)
        assertEquals(revokerId, resource.revokedByUserId)
    }

    @Test
    fun `toDetailResource - Name nobody behind a grant revoked with its consent`() {
        val resource = mapper.toDetailResource(
            GrantWithStatus(grant(revokedBy = GrantRevokedBy.CONSENT_REVOKED), GrantStatus.REVOKED)
        )

        assertEquals("consent_revoked", resource.revokedBy)
        assertNull(resource.revokedByUserId)
    }

    @Test
    fun `toDetailResource - Publish the claims the request asked for per member of the claims parameter`() {
        val resource = mapper.toDetailResource(
            GrantWithStatus(
                grant(requestedClaims = RequestedClaims.of(arrayOf("email"), arrayOf("shoe_size"))),
                GrantStatus.LIVE
            )
        )

        assertEquals(listOf("email"), resource.requestedClaims?.idToken)
        assertEquals(listOf("shoe_size"), resource.requestedClaims?.userinfo)
    }

    @Test
    fun `toDetailResource - Publish no requested claims where the request asked for none`() {
        assertNull(mapper.toDetailResource(GrantWithStatus(grant(), GrantStatus.LIVE)).requestedClaims)
    }
}
