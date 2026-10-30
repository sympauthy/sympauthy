package com.sympauthy.api.controller.openid.discovery

import com.sympauthy.api.resource.openid.OpenIdConfigurationResource
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.ResponseType
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.business.model.user.claim.PersonalClaim
import com.sympauthy.config.model.*
import com.sympauthy.util.wireName
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

/**
 * Holds the discovery document to what this server implements.
 *
 * The document is the machine-readable contract a conforming client configures itself from. A
 * capability advertised and not served lands its failure on a correct client doing exactly what it
 * was told; one served and not advertised cannot be found by a client that reads capabilities rather
 * than documentation. Neither has a symptom of its own — the document renders, the endpoints work,
 * and nothing compares the two.
 *
 * Each advertised set is held in both directions. The literal is what is true today, so a set that
 * grows fails here before it reaches a client, which is where the question *does the server actually
 * do this?* gets asked. The comparison against the enum is the other direction, and catches a
 * document hand-written again even where the literal was updated to agree with it.
 *
 * The client authentication methods, the response mode and the subject type have only the literal,
 * because nothing dispatches on a closed set of any of them — an enum there would have this document
 * as its one reader, which relocates the literal rather than deriving it. Naming them here is the only
 * guard they can have.
 */
@ExtendWith(MockKExtension::class)
class OpenIdConfigurationControllerTest {

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var uncheckedAdvancedConfig: EnabledAdvancedConfig

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
        interactiveFlow = InteractiveFlowConfig(expiration = java.time.Duration.ofMinutes(30)),
        grant = GrantConfig(expiration = null),
        identifierClaims = emptyList(),
        userMergingEnabled = false,
        byPassword = ByPasswordConfig(enabled = false)
    )

    private val uncheckedUrlsConfig: UrlsConfig = EnabledUrlsConfig(
        root = URI.create("https://auth.example.com")
    )

    @InjectMockKs
    lateinit var controller: OpenIdConfigurationController

    @Test
    fun `getConfiguration - Advertises the response types the authorization endpoint implements`() = runTest {
        val configuration = configuration()

        assertEquals(listOf("code"), configuration.responseTypesSupported)
        assertEquals(ResponseType.entries.map { it.wireName }, configuration.responseTypesSupported)
    }

    @Test
    fun `getConfiguration - Advertises the grant types the token endpoint dispatches on`() = runTest {
        val configuration = configuration()

        assertEquals(
            listOf(
                "authorization_code",
                "refresh_token",
                "client_credentials",
                "urn:ietf:params:oauth:grant-type:token-exchange"
            ),
            configuration.grantTypesSupported
        )
        assertEquals(GrantType.entries.map { it.wireName }, configuration.grantTypesSupported)
    }

    @Test
    fun `getConfiguration - Advertises the code challenge methods PKCE verifies`() = runTest {
        val configuration = configuration()

        assertEquals(listOf("S256"), configuration.codeChallengeMethodsSupported)
        assertEquals(CodeChallengeMethod.entries.map { it.value }, configuration.codeChallengeMethodsSupported)
    }

    @Test
    fun `getConfiguration - Advertises the one response mode the authorization endpoint answers with`() = runTest {
        assertEquals(listOf("query"), configuration().responseModesSupported)
    }

    @Test
    fun `getConfiguration - Advertises that a client may send no request object`() = runTest {
        val configuration = configuration()

        assertEquals(false, configuration.requestParameterSupported)
        assertEquals(false, configuration.requestUriParameterSupported)
        assertEquals(false, configuration.requireRequestUriRegistration)
    }

    @Test
    fun `getConfiguration - Advertises the one claim type this server returns`() = runTest {
        assertEquals(listOf("normal"), configuration().claimTypesSupported)
    }

    @Test
    fun `getConfiguration - Advertises the client authentication methods the token endpoint accepts`() = runTest {
        assertEquals(
            listOf("client_secret_basic", "client_secret_post", "none"),
            configuration().tokenEndpointAuthMethodsSupported
        )
    }

    @Test
    fun `getConfiguration - Advertises the client authentication methods the revocation endpoint accepts`() =
        runTest {
            assertEquals(
                listOf("client_secret_basic", "client_secret_post", "none"),
                configuration().revocationEndpointAuthMethodsSupported
            )
        }

    @Test
    fun `getConfiguration - Advertises the client authentication methods the introspection endpoint accepts`() =
        runTest {
            assertEquals(
                listOf("client_secret_basic", "client_secret_post"),
                configuration().introspectionEndpointAuthMethodsSupported
            )
        }

    @Test
    fun `getConfiguration - Advertises the one subject type this server issues`() = runTest {
        assertEquals(listOf("public"), configuration().subjectTypesSupported)
    }

    @Test
    fun `getConfiguration - Advertises that a client may send the claims parameter`() = runTest {
        assertEquals(true, configuration().claimsParameterSupported)
    }

    @Test
    fun `getConfiguration - Advertises a claim whose file names the discovery document`() = runTest {
        val configuration = configurationWithClaims(
            claim(OpenIdConnectClaimId.EMAIL, verifiedId = OpenIdConnectClaimId.EMAIL_VERIFIED)
        )

        assertEquals(
            listOf(OpenIdConnectClaimId.EMAIL, OpenIdConnectClaimId.EMAIL_VERIFIED),
            configuration.claimsSupported
        )
    }

    @Test
    fun `getConfiguration - Advertises a claim of the deployment's own that names the document`() = runTest {
        val configuration = configurationWithClaims(claim("loyalty_tier"))

        assertEquals(listOf("loyalty_tier"), configuration.claimsSupported)
    }

    @Test
    fun `getConfiguration - Advertises no claim the deployment carries and does not advertise`() = runTest {
        val configuration = configurationWithClaims(
            claim(OpenIdConnectClaimId.NAME),
            claim(OpenIdConnectClaimId.EMAIL, publishedIn = setOf(ClaimPublicationPlace.ID_TOKEN))
        )

        assertEquals(listOf(OpenIdConnectClaimId.NAME), configuration.claimsSupported)
    }

    private suspend fun configurationWithClaims(vararg claims: EnabledClaim): OpenIdConfigurationResource {
        coEvery { scopeManager.listEnabledScopes() } returns emptyList()
        every { claimManager.listEnabledClaims() } returns claims.toList()
        every { uncheckedAdvancedConfig.publicJwtAlgorithm } returns JwtAlgorithm.ES256
        return controller.getConfiguration()
    }

    private fun claim(
        id: String,
        verifiedId: String? = null,
        publishedIn: Set<ClaimPublicationPlace> = ClaimPublicationPlace.entries.toSet()
    ) = PersonalClaim(
        id = id,
        verifiedId = verifiedId,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        allowedValues = null,
        publishedIn = publishedIn,
        publishedInWhenRequested = emptySet(),
        acl = ClaimAcl(
            collectedInFlow = null,
            readableByPerson = ClaimAclExpression("true"),
            writableByPerson = null,
            readableByClient = ClaimAclExpression("true"),
            writableByClient = null
        )
    )

    /**
     * The document built from a deployment serving no scope and no claim, which is every value this
     * class asserts on: none of them is derived from either.
     */
    private suspend fun configuration(): OpenIdConfigurationResource {
        coEvery { scopeManager.listEnabledScopes() } returns emptyList()
        every { claimManager.listEnabledClaims() } returns emptyList()
        every { uncheckedAdvancedConfig.publicJwtAlgorithm } returns JwtAlgorithm.ES256
        return controller.getConfiguration()
    }
}
