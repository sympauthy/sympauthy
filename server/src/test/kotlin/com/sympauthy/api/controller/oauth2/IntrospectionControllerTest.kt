package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.model.*
import io.micronaut.http.HttpRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@ExtendWith(MockKExtension::class)
class IntrospectionControllerTest {

    @MockK
    lateinit var tokenManager: TokenManager

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var clientAuthenticationUtil: ClientAuthenticationUtil

    private val uncheckedAuthConfig: AuthConfig = EnabledAuthConfig(
        issuer = "https://issuer.example.com",
        token = TokenConfig(
            accessExpiration = java.time.Duration.ofHours(1),
            idExpiration = java.time.Duration.ofHours(1),
            refreshEnabled = true,
            refreshExpiration = java.time.Duration.ofDays(30),
            dpopRequired = false
        ),
        authorizationCode = AuthorizationCodeConfig(
            expiration = java.time.Duration.ofMinutes(30)
        ),
        identifierClaims = emptyList(),
        userMergingEnabled = false,
        byPassword = ByPasswordConfig(enabled = false)
    )

    @InjectMockKs
    lateinit var controller: IntrospectionController

    /** A client the audience of an active token is read from; an inactive one names no audience. */
    private fun mockClientWithTokenAudience(): Client = mockk {
        every { audience } returns mockk {
            every { tokenAudience } returns "https://test-audience"
        }
    }

    /** A client the claims of the person a token was issued for are read for the audience of. */
    private fun mockClientOfAudience(): Client = mockk {
        every { audience } returns mockk {
            every { tokenAudience } returns "https://test-audience"
            every { id } returns AUDIENCE
        }
    }

    private fun stubPublishedClaims(userId: UUID, claims: List<CollectedClaim>) {
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
                userId, AUDIENCE, ClaimPublicationPlace.INTROSPECTION, listOf(CONSENTED_SCOPE), emptyList()
            )
        } returns claims
    }

    private fun collected(userId: UUID, id: String, value: Any) = CollectedClaim(
        userId = userId,
        claim = claim(id),
        value = value,
        verified = null,
        collectionDate = LocalDateTime.of(2025, 1, 1, 0, 0, 0),
        verificationDate = null
    )

    private fun claim(id: String) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        kind = ClaimKind.PERSONAL,
        group = null,
        required = false,
        generated = false,
        collectedInFlow = false,
        allowedValues = null,
        publishedIn = setOf(ClaimPublicationPlace.INTROSPECTION),
        publishedInWhenRequested = emptySet(),
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByPerson = false,
                collectedInFlow = false,
                writableByPerson = false,
                readableByClient = true,
                writableByClient = false,
                writeMaxAuthenticationAge = null
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList()
            )
        )
    )

    @Test
    fun `introspectToken - Returns active response for valid token`() = runTest {
        val request = mockk<HttpRequest<*>>()
        val client = mockClientOfAudience()
        val userId = UUID.randomUUID()
        val tokenId = UUID.randomUUID()
        val issueDate = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        val expirationDate = LocalDateTime.of(2025, 1, 1, 1, 0, 0)
        val authenticationDate = LocalDateTime.of(2024, 12, 31, 23, 45, 0)

        val token = mockPersonToken(tokenId, userId, authenticationDate, issueDate, expirationDate)

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", null) } returns token
        stubPublishedClaims(userId, emptyList())

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertTrue(result.active)
        assertEquals("openid profile", result.scope)
        assertEquals("test-client", result.clientId)
        assertEquals("Bearer", result.tokenType)
        assertEquals(userId.toString(), result.sub)
        assertEquals("https://test-audience", result.aud)
        assertEquals("https://issuer.example.com", result.iss)
        assertEquals(tokenId.toString(), result.jti)
        assertNotNull(result.exp)
        assertNotNull(result.iat)
        assertEquals(authenticationDate.toEpochSecond(ZoneOffset.UTC), result.authTime)
    }

    private fun mockPersonToken(
        tokenId: UUID,
        userId: UUID,
        authenticationDate: LocalDateTime?,
        issueDate: LocalDateTime,
        expirationDate: LocalDateTime?
    ): AuthenticationToken = mockk {
        every { id } returns tokenId
        every { clientId } returns "test-client"
        every { this@mockk.userId } returns userId
        every { allScopes } returns listOf("openid", CONSENTED_SCOPE)
        every { consentedScopes } returns listOf(CONSENTED_SCOPE)
        every { clientScopes } returns emptyList()
        every { dpopJkt } returns null
        every { this@mockk.authenticationDate } returns authenticationDate
        every { this@mockk.issueDate } returns issueDate
        every { this@mockk.expirationDate } returns expirationDate
    }

    @Test
    fun `introspectToken - Answers the claims the deployment publishes in the introspection response`() =
        runTest {
            val request = mockk<HttpRequest<*>>()
            val client = mockClientOfAudience()
            val userId = UUID.randomUUID()
            val token = mockPersonToken(
                UUID.randomUUID(), userId, null, LocalDateTime.of(2025, 1, 1, 0, 0, 0), null
            )

            coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
            coEvery { tokenManager.introspectToken(client, "the-token", null) } returns token
            stubPublishedClaims(userId, listOf(collected(userId, "loyalty_tier", "gold")))

            val result = controller.introspectToken(
                request = request,
                token = "the-token",
                tokenTypeHint = null,
                clientId = "test-client",
                clientSecret = "secret"
            )

            assertEquals(mapOf("loyalty_tier" to "gold"), result.additionalClaims)
        }

    @Test
    fun `introspectToken - Leaves out a claim named after a member the response declares`() = runTest {
        val request = mockk<HttpRequest<*>>()
        val client = mockClientOfAudience()
        val userId = UUID.randomUUID()
        val token = mockPersonToken(
            UUID.randomUUID(), userId, null, LocalDateTime.of(2025, 1, 1, 0, 0, 0), null
        )

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", null) } returns token
        stubPublishedClaims(userId, listOf(collected(userId, "scope", "everything")))

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertEquals(emptyMap<String, Any>(), result.additionalClaims)
        assertEquals("openid $CONSENTED_SCOPE", result.scope)
    }

    @Test
    fun `introspectToken - Returns inactive response for invalid token`() = runTest {
        val request = mockk<HttpRequest<*>>()
        val client = mockk<Client>()

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "bad-token", null) } returns null

        val result = controller.introspectToken(
            request = request,
            token = "bad-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertFalse(result.active)
        assertNull(result.scope)
        assertNull(result.clientId)
        assertNull(result.sub)
        assertNull(result.jti)
    }

    @Test
    fun `introspectToken - Throws when token parameter is missing`() = runTest {
        val request = mockk<HttpRequest<*>>()
        val client = mockk<Client>()

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client

        val exception = assertThrows<OAuth2Exception> {
            controller.introspectToken(
                request = request,
                token = null,
                tokenTypeHint = null,
                clientId = "test-client",
                clientSecret = "secret"
            )
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
    }

    @Test
    fun `introspectToken - Throws when client authentication fails`() = runTest {
        val request = mockk<HttpRequest<*>>()

        coEvery {
            clientAuthenticationUtil.resolveClient(request, "bad-client", "wrong")
        } throws oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")

        assertThrows<OAuth2Exception> {
            controller.introspectToken(
                request = request,
                token = "some-token",
                tokenTypeHint = null,
                clientId = "bad-client",
                clientSecret = "wrong"
            )
        }
    }

    @Test
    fun `introspectToken - Returns DPoP token type when dpopJkt is present`() = runTest {
        val request = mockk<HttpRequest<*>>()
        val client = mockClientWithTokenAudience()
        val token = mockk<AuthenticationToken> {
            every { id } returns UUID.randomUUID()
            every { clientId } returns "test-client"
            every { userId } returns null
            every { allScopes } returns emptyList()
            every { dpopJkt } returns "some-thumbprint"
            every { authenticationDate } returns null
            every { issueDate } returns LocalDateTime.of(2025, 1, 1, 0, 0, 0)
            every { expirationDate } returns null
        }

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", null) } returns token

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertTrue(result.active)
        assertEquals("DPoP", result.tokenType)
    }

    @Test
    fun `introspectToken - Uses client ID as subject for client_credentials tokens`() = runTest {
        val request = mockk<HttpRequest<*>>()
        val client = mockClientWithTokenAudience()
        val token = mockk<AuthenticationToken> {
            every { id } returns UUID.randomUUID()
            every { clientId } returns "test-client"
            every { userId } returns null
            every { allScopes } returns listOf("read")
            every { dpopJkt } returns null
            every { authenticationDate } returns null
            every { issueDate } returns LocalDateTime.of(2025, 1, 1, 0, 0, 0)
            every { expirationDate } returns null
        }

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", null) } returns token

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertTrue(result.active)
        assertEquals("test-client", result.sub)
        assertNull(result.authTime)
    }

    @Test
    fun `introspectToken - Passes token_type_hint to manager`() = runTest {
        val request = mockk<HttpRequest<*>>()
        val client = mockk<Client>()

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", "refresh_token") } returns null

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = "refresh_token",
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertFalse(result.active)
    }

    private companion object {

        const val AUDIENCE = "storefront"
        const val CONSENTED_SCOPE = "profile"
    }
}
