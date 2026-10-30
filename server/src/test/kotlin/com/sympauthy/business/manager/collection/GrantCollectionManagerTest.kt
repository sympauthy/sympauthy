package com.sympauthy.business.manager.collection

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.util.criteriaOf
import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.grant.GrantStatus
import com.sympauthy.business.model.grant.GrantWithStatus
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.RequestedClaims
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class GrantCollectionManagerTest {

    @MockK
    lateinit var grantManager: GrantManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var audienceManager: AudienceManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @InjectMockKs
    lateinit var grantCollectionManager: GrantCollectionManager

    private val createdAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    private val anaId = UUID.randomUUID()

    private val email = mockk<EnabledClaim> { every { id } returns "email" }

    /** An identifier claim named as one of the grant's own fields, which the grant's field wins over. */
    private val status = mockk<EnabledClaim> { every { id } returns "status" }

    private val firstPage = PageParams(page = 0, size = 20)

    @BeforeEach
    fun knownConfiguration() {
        coEvery { clientManager.listClients() } returns listOf("web", "backend").map { clientId ->
            mockk<Client> { every { id } returns clientId }
        }
        every { audienceManager.listAudiences() } returns listOf(
            Audience(id = "default", tokenAudience = "default")
        )
        coEvery { scopeManager.listEnabledScopes() } returns listOf("openid", "profile", "read").map { scopeId ->
            mockk<EnabledScope> { every { scope } returns scopeId }
        }
        every { claimManager.listIdentifierClaims() } returns listOf(email, status)
    }

    private fun grant(
        id: UUID = UUID.randomUUID(),
        userId: UUID? = anaId,
        clientId: String = "web",
        grantType: GrantType = GrantType.AUTHORIZATION_CODE,
        consentedScopes: List<String> = listOf("openid"),
        clientScopes: List<String> = emptyList(),
        creationDate: LocalDateTime = createdAt,
        revokedBy: GrantRevokedBy? = null,
        actorGrantId: UUID? = null,
        status: GrantStatus = GrantStatus.LIVE
    ) = GrantWithStatus(
        Grant(
            id = id,
            userId = userId,
            clientId = clientId,
            audienceId = "default",
            grantType = grantType,
            grantedScopes = emptyList(),
            grantedAt = null,
            grantedBy = null,
            consentedScopes = consentedScopes,
            consentedAt = null,
            consentedBy = null,
            clientScopes = clientScopes,
            authenticationDate = null,
            requestedClaims = RequestedClaims.NONE,
            sessionId = null,
            actorGrantId = actorGrantId,
            creationDate = creationDate,
            expirationDate = null,
            revokedAt = revokedBy?.let { creationDate.plusHours(1) },
            revokedBy = revokedBy,
            revokedById = null
        ),
        status
    )

    private val clientCredentials = grant(
        userId = null,
        clientId = "backend",
        grantType = GrantType.CLIENT_CREDENTIALS,
        consentedScopes = emptyList(),
        clientScopes = listOf("read"),
        creationDate = createdAt.plusMinutes(1)
    )

    private fun knownGrants(vararg grants: GrantWithStatus) {
        coEvery { grantManager.listWithStatus() } returns grants.toList()
    }

    @Test
    fun `listGrants - Read the oldest grant first where no order is asked for`() = runTest {
        val newest = grant(creationDate = createdAt.plusDays(1))
        val oldest = grant()
        knownGrants(newest, oldest)

        val result = grantCollectionManager.listGrants(criteriaOf(), firstPage)

        assertEquals(listOf(oldest, newest), result.items)
    }

    @Test
    fun `listGrants - Keep the grants no user holds where user_id is asked to be absent`() = runTest {
        knownGrants(grant(), clientCredentials)

        val result = grantCollectionManager.listGrants(criteriaOf("user_id.is_null" to "true"), firstPage)

        assertEquals(listOf(clientCredentials), result.items)
    }

    @Test
    fun `listGrants - Keep the grants in the status a criterion names`() = runTest {
        val revoked = grant(revokedBy = GrantRevokedBy.ADMIN, status = GrantStatus.REVOKED)
        knownGrants(grant(), revoked)

        val result = grantCollectionManager.listGrants(criteriaOf("status" to "revoked"), firstPage)

        assertEquals(listOf(revoked), result.items)
    }

    @Test
    fun `listGrants - Match a scope in any of the sets a grant holds`() = runTest {
        val consented = grant()
        knownGrants(consented, clientCredentials)

        assertEquals(
            listOf(clientCredentials),
            grantCollectionManager.listGrants(criteriaOf("scope" to "read"), firstPage).items
        )
        assertEquals(
            listOf(clientCredentials),
            grantCollectionManager.listGrants(criteriaOf("scope.ne" to "openid"), firstPage).items
        )
    }

    @Test
    fun `listGrants - Keep the grants exchanged from the grant a criterion names`() = runTest {
        val actor = clientCredentials
        val exchanged = grant(grantType = GrantType.TOKEN_EXCHANGE, actorGrantId = actor.grant.id)
        knownGrants(grant(), actor, exchanged)

        val result = grantCollectionManager.listGrants(
            criteriaOf("actor_grant_id" to actor.grant.id.toString()),
            firstPage
        )

        assertEquals(listOf(exchanged), result.items)
    }

    @Test
    fun `listGrants - Match the free text against the identifier claims of the user`() = runTest {
        val anas = grant()
        val other = grant(userId = UUID.randomUUID())
        knownGrants(anas, other, clientCredentials)
        coEvery { collectedClaimManager.listIdentifierByUserIds(any()) } returns listOf(
            CollectedClaim(
                userId = anaId,
                claim = email,
                value = "Ana@Example.com",
                verified = true,
                collectionDate = createdAt,
                verificationDate = null
            )
        )

        val result = grantCollectionManager.listGrants(criteriaOf(query = "ana@example"), firstPage)

        assertEquals(listOf(anas), result.items)
    }

    @Test
    fun `listGrants - Match the free text against the client of a grant no user holds`() = runTest {
        knownGrants(grant(), clientCredentials)
        coEvery { collectedClaimManager.listIdentifierByUserIds(any()) } returns emptyList()

        val result = grantCollectionManager.listGrants(criteriaOf(query = "back"), firstPage)

        assertEquals(listOf(clientCredentials), result.items)
    }

    @Test
    fun `listGrants - Read no identifier claim where no free text was sent`() = runTest {
        knownGrants(grant())

        grantCollectionManager.listGrants(criteriaOf(), firstPage)

        coVerify(exactly = 0) { collectedClaimManager.listIdentifierByUserIds(any()) }
    }

    @Test
    fun `capabilities - Publish an identifier claim as a field searched and never filtered on`() = runTest {
        val capabilities = grantCollectionManager.capabilities()

        assertEquals(listOf("client_id", "audience_id", "email"), capabilities.searchFields.map { it.name })
        assertFalse(capabilities.filterFields.any { it.name == "email" })
        assertEquals("fields.grant_status", capabilities.fieldOrNull("status")?.key)
    }

    @Test
    fun `capabilities - Enumerate every grant type a grant is opened by, and no refresh`() = runTest {
        val grantType = grantCollectionManager.capabilities().fieldOrNull("grant_type")

        assertEquals(
            listOf("authorization_code", "client_credentials", "urn:ietf:params:oauth:grant-type:token-exchange"),
            grantType?.values?.map { it.value }
        )
    }

    @Test
    fun `capabilities - Refuse a filter on an identifier claim`() = runTest {
        assertThrows<LocalizedHttpException> { criteriaOf("email" to "ana@example.com") }
    }

    @Test
    fun `capabilities - Order on the dates alone`() = runTest {
        assertEquals(
            listOf("created_at", "expires_at", "revoked_at"),
            grantCollectionManager.capabilities().sortFields.map { it.name }
        )
    }

    private suspend fun criteriaOf(
        vararg filters: Pair<String, String>,
        sort: String? = null,
        query: String? = null
    ): CollectionCriteria = grantCollectionManager.capabilities().criteriaOf(*filters, sort = sort, query = query)
}
