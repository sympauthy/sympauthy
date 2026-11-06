package com.sympauthy.api.mapper.admin

import com.sympauthy.business.manager.collection.GrantCollectionManager.GrantWithUser
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.grant.GrantStatus
import com.sympauthy.business.model.grant.GrantWithStatus
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.UserWithIdentifierClaims
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.RequestedClaims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class AdminGrantResourceMapperTest {

    private val mapper = AdminGrantResourceMapper(AdminUserResourceMapper())

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

    private fun grantWithUser(grant: Grant, status: GrantStatus = GrantStatus.LIVE) =
        GrantWithUser(GrantWithStatus(grant, status), user = null)

    @Test
    fun `toSummaryResource - Publish the grant type and the status under the names they travel by`() {
        val resource = mapper.toSummaryResource(
            grantWithUser(grant(grantType = GrantType.TOKEN_EXCHANGE), GrantStatus.LAPSED)
        )

        assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", resource.grantType)
        assertEquals("lapsed", resource.status)
    }

    @Test
    fun `toSummaryResource - Name the person by their identifier claims, and nothing of their activity`() {
        val grant = grant()
        val user = User(
            id = grant.userId!!,
            status = UserStatus.ENABLED,
            creationDate = createdAt,
            sessionId = null,
            lastAuthenticationDate = createdAt,
            lastRefreshDate = createdAt
        )
        val email = CollectedClaim(
            userId = user.id,
            claim = mockk<EnabledClaim> { every { id } returns "email" },
            value = "ana@example.com",
            verified = true,
            collectionDate = createdAt,
            verificationDate = createdAt
        )

        val resource = mapper.toSummaryResource(
            GrantWithUser(GrantWithStatus(grant, GrantStatus.LIVE), UserWithIdentifierClaims(user, listOf(email)))
        )

        assertEquals(user.id, resource.user?.userId)
        assertEquals(mapOf("email" to "ana@example.com"), resource.user?.claims)
        assertNull(resource.user?.lastAuthenticatedAt)
        assertNull(resource.user?.lastRefreshedAt)
    }

    @Test
    fun `toSummaryResource - Name nobody on a grant no person holds`() {
        assertNull(mapper.toSummaryResource(grantWithUser(grant(grantType = GrantType.CLIENT_CREDENTIALS))).user)
    }

    @Test
    fun `toSummaryResource - Publish the three scope sets the grant holds`() {
        val resource = mapper.toSummaryResource(grantWithUser(grant()))

        assertEquals(listOf("admin:grants:read"), resource.grantedScopes)
        assertEquals(listOf("openid"), resource.consentedScopes)
        assertEquals(emptyList<String>(), resource.clientScopes)
    }

    @Test
    fun `toDetailResource - Name the administrator who revoked the grant`() {
        val resource = mapper.toDetailResource(
            grantWithUser(grant(revokedBy = GrantRevokedBy.ADMIN), GrantStatus.REVOKED)
        )

        assertEquals("admin", resource.revokedBy)
        assertEquals(revokerId, resource.revokedByUserId)
    }

    @Test
    fun `toDetailResource - Name nobody behind a grant revoked with its consent`() {
        val resource = mapper.toDetailResource(
            grantWithUser(grant(revokedBy = GrantRevokedBy.CONSENT_REVOKED), GrantStatus.REVOKED)
        )

        assertEquals("consent_revoked", resource.revokedBy)
        assertNull(resource.revokedByUserId)
    }

    @Test
    fun `toDetailResource - Publish the claims the request asked for per member of the claims parameter`() {
        val resource = mapper.toDetailResource(
            grantWithUser(grant(requestedClaims = RequestedClaims.of(arrayOf("email"), arrayOf("shoe_size"))))
        )

        assertEquals(listOf("email"), resource.requestedClaims?.idToken)
        assertEquals(listOf("shoe_size"), resource.requestedClaims?.userinfo)
    }

    @Test
    fun `toDetailResource - Publish no requested claims where the request asked for none`() {
        assertNull(mapper.toDetailResource(grantWithUser(grant())).requestedClaims)
    }
}
