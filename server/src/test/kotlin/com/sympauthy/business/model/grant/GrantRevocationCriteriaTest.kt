package com.sympauthy.business.model.grant

import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.user.claim.RequestedClaims
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class GrantRevocationCriteriaTest {

    private val userId = UUID.randomUUID()

    private fun grant(userId: UUID? = this.userId) = Grant(
        id = UUID.randomUUID(),
        userId = userId,
        clientId = "web",
        audienceId = "default",
        grantType = if (userId == null) GrantType.CLIENT_CREDENTIALS else GrantType.AUTHORIZATION_CODE,
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
        creationDate = LocalDateTime.of(2026, 1, 1, 0, 0),
        expirationDate = null,
        revokedAt = null,
        revokedBy = null,
        revokedById = null
    )

    @Test
    fun `matches - Reach a grant every criterion given holds`() {
        val criteria = GrantRevocationCriteria(userIds = setOf(userId), clientIds = setOf("web", "mobile"))

        assertTrue(criteria.matches(grant()))
    }

    @Test
    fun `matches - Leave a grant one criterion given does not hold`() {
        val criteria = GrantRevocationCriteria(userIds = setOf(userId), audienceIds = setOf("partners"))

        assertFalse(criteria.matches(grant()))
    }

    @Test
    fun `matches - Reach no grant through an empty set`() {
        assertFalse(GrantRevocationCriteria(grantIds = emptySet()).matches(grant()))
    }

    @Test
    fun `matches - Leave a grant no user holds out of criteria on users`() {
        assertFalse(GrantRevocationCriteria(userIds = setOf(userId)).matches(grant(userId = null)))
    }

    @Test
    fun `isUnbounded - Hold criteria giving no set as unbounded, and an empty set as bounded`() {
        assertTrue(GrantRevocationCriteria().isUnbounded)
        assertFalse(GrantRevocationCriteria(clientIds = emptySet()).isUnbounded)
    }
}
