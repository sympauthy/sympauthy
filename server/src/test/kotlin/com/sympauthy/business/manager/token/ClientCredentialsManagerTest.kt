package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.ClientGrantScopesResult
import com.sympauthy.business.manager.auth.ClientScopeGrantingManager
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_SCOPE
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class ClientCredentialsManagerTest {

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var clientScopeGrantingManager: ClientScopeGrantingManager

    @MockK
    lateinit var grantManager: GrantManager

    @MockK
    lateinit var accessTokenGenerator: AccessTokenGenerator

    @MockK
    lateinit var claimAclManager: ClaimAclManager

    @SpyK
    @InjectMockKs
    lateinit var manager: ClientCredentialsManager

    /** The doors every token of one response comes through. */
    private val doors = mockk<ClaimAclTokenDoors>()

    /**
     * Stubs the doors of a token issued under [grant] to [client], bound where [dpopJkt] is set, as [doors]: a
     * generator handed any other set was handed doors the response did not build for it.
     */
    private fun stubDoors(grant: Grant, client: Client, dpopJkt: String?) {
        every { grant.consentedScopes } returns emptyList()
        every { grant.grantedScopes } returns emptyList()
        every { grant.clientScopes } returns emptyList()
        every { grant.authenticationDate } returns null
        val context = ClaimAclContext.of(grant, client, dpopJkt)
        every { claimAclManager.getTokenDoors(context, any()) } returns doors
    }

    @Test
    fun `generateAccessToken - Issues the access token under the grant it opens`() = runTest {
        val audience = mockk<Audience> { every { id } returns "audience" }
        val client = mockk<Client> {
            every { id } returns "client"
            every { this@mockk.audience } returns audience
        }
        val grant = mockk<Grant> { every { grantType } returns GrantType.CLIENT_CREDENTIALS }
        val accessToken = mockk<EncodedAuthenticationToken>()
        coEvery {
            grantManager.createClientCredentialsGrant("client", "audience", listOf("scope"))
        } returns grant
        stubDoors(grant, client, "jkt")
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.CLIENT_CREDENTIALS, "jkt")
        } returns accessToken

        assertSame(accessToken, manager.generateAccessToken(client, listOf("scope"), "jkt"))
    }

    @Test
    fun `issue - Issues an access token for the client scopes granted`() = runTest {
        val client = mockk<Client>()
        val scope = mockk<ClientScope> { every { this@mockk.scope } returns "read" }
        val accessToken = mockk<EncodedAuthenticationToken>()
        coEvery { scopeManager.parseRequestedClientScopes(client, "read") } returns listOf(scope)
        coEvery { clientScopeGrantingManager.grantClientScopes(client, listOf(scope)) } returns ClientGrantScopesResult(
            requestedScopes = listOf(scope),
            results = listOf(ScopeGrantingMethodResult(grantedScopes = listOf(scope), declinedScopes = emptyList()))
        )
        coEvery { manager.generateAccessToken(client, listOf("read"), null) } returns accessToken

        val result = manager.issue(client, TokenRequest.ClientCredentials("read"), null)

        assertSame(accessToken, result.accessToken)
        assertNull(result.refreshToken)
        assertNull(result.idToken)
    }

    @Test
    fun `issue - Throws invalid_scope when a requested scope is refused`() = runTest {
        val client = mockk<Client>()
        coEvery {
            scopeManager.parseRequestedClientScopes(client, "doesnotexist")
        } throws businessExceptionOf("scope.unsupported", "scope" to "doesnotexist")

        val exception = assertThrows<OAuth2Exception> {
            manager.issue(client, TokenRequest.ClientCredentials("doesnotexist"), null)
        }

        assertEquals(INVALID_SCOPE, exception.errorCode)
        assertEquals("scope.unsupported", exception.detailsId)
        assertEquals(mapOf("scope" to "doesnotexist"), exception.values)
        coVerify(exactly = 0) { manager.generateAccessToken(any(), any(), any()) }
    }

    @Test
    fun `issue - Lets a failure of this server through rather than blaming the scope`() = runTest {
        val client = mockk<Client>()
        val failure = BusinessException(
            recoverable = false,
            detailsId = "mapper.scope.invalid_property",
            recommendedStatus = INTERNAL_SERVER_ERROR
        )
        coEvery { scopeManager.parseRequestedClientScopes(client, "read") } throws failure

        val exception = assertThrows<BusinessException> {
            manager.issue(client, TokenRequest.ClientCredentials("read"), null)
        }

        assertSame(failure, exception)
    }
}
