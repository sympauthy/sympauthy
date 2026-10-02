package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jwt.JWTClaimsSet
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.TokenConfig
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class AccessTokenGeneratorTest {

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var tokenMapper: EncodedAuthenticationTokenMapper

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    @InjectMockKs
    lateinit var generator: AccessTokenGenerator

    private val readAudienceId = slot<String>()

    @Test
    fun `generateAccessToken - Claim the values the deployment publishes in the access token`() = runTest {
        val claimsSet = issue(
            collected(claim("loyalty_tier", ClaimDataType.STRING), "gold"),
            collected(claim("age", ClaimDataType.NUMBER), 42L)
        )

        assertEquals("gold", claimsSet.getStringClaim("loyalty_tier"))
        assertEquals(42L, claimsSet.getLongClaim("age"))
    }

    @Test
    fun `generateAccessToken - Claim the verified companion beside the value it answers for`() = runTest {
        val claimsSet = issue(
            collected(
                claim("email", ClaimDataType.EMAIL, verifiedId = "email_verified"),
                "ada@example.com",
                verified = true
            )
        )

        assertEquals(true, claimsSet.getBooleanClaim("email_verified"))
    }

    @Test
    fun `generateAccessToken - Claim the address object OpenID Connect defines`() = runTest {
        val claimsSet = issue(
            collected(claim("locality", ClaimDataType.STRING, ClaimGroup.ADDRESS), "Brussels"),
            collected(claim("country", ClaimDataType.STRING, ClaimGroup.ADDRESS), "Belgium")
        )

        val address = claimsSet.getJSONObjectClaim("address")
        assertEquals("Brussels", address["locality"])
        assertEquals("Brussels\nBelgium", address["formatted"])
    }

    @Test
    fun `generateAccessToken - Claim nothing over a member the token states about its authorization`() =
        runTest {
            val claimsSet = issue(collected(claim("scope", ClaimDataType.STRING), "everything"))

            assertEquals(CONSENTED_SCOPE, claimsSet.getStringClaim("scope"))
        }

    @Test
    fun `generateAccessToken - Read the claims for the audience the token is issued for`() = runTest {
        issue()

        assertEquals(AUDIENCE_ID, readAudienceId.captured)
    }

    @Test
    fun `generateAccessTokenForClient - Claim nothing about a person`() = runTest {
        val builder = stubGeneration()

        generator.generateAccessTokenForClient(
            clientId = "client",
            audience = audience(),
            clientScopes = listOf("users:claims:read")
        )

        val claimsSet = builder.build()
        assertEquals("client", claimsSet.subject)
        assertNull(claimsSet.getClaim("loyalty_tier"))
        assertFalse(claimsSet.claims.containsKey("address"))
    }

    private fun audience() = Audience(id = AUDIENCE_ID, tokenAudience = TOKEN_AUDIENCE)

    private fun claim(
        id: String,
        dataType: ClaimDataType,
        group: ClaimGroup? = null,
        verifiedId: String? = null
    ) = Claim(
        id = id,
        enabled = true,
        verifiedId = verifiedId,
        dataType = dataType,
        kind = ClaimKind.PERSONAL,
        group = group,
        required = false,
        generated = false,
        collectedInFlow = true,
        allowedValues = null,
        audienceId = null,
        publishedIn = setOf(ClaimPublicationPlace.ACCESS_TOKEN),
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByPerson = true,
                collectedInFlow = true,
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

    private fun collected(claim: Claim, value: Any?, verified: Boolean? = null) = CollectedClaim(
        userId = UUID.randomUUID(),
        claim = claim,
        value = value,
        verified = verified,
        collectionDate = LocalDateTime.now(),
        verificationDate = null
    )

    private suspend fun issue(vararg claims: CollectedClaim): JWTClaimsSet {
        val userId = UUID.randomUUID()
        val builder = stubGeneration(userId, claims.toList())

        generator.generateAccessToken(
            userId = userId,
            clientId = "client",
            audience = audience(),
            grantedScopes = emptyList(),
            grantedAt = null,
            grantedBy = null,
            consentedScopes = listOf(CONSENTED_SCOPE),
            consentedAt = null,
            consentedBy = null,
            clientScopes = emptyList(),
            sessionId = null,
            grantType = "authorization_code"
        )

        return builder.build()
    }

    private fun stubGeneration(
        userId: UUID? = null,
        claims: List<CollectedClaim> = emptyList()
    ): JWTClaimsSet.Builder {
        every { uncheckedAuthConfig.token } returns mockk<TokenConfig> {
            every { accessExpiration } returns Duration.ofMinutes(5)
        }
        userId?.let {
            coEvery {
                consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
                    it, capture(readAudienceId), ClaimPublicationPlace.ACCESS_TOKEN,
                    listOf(CONSENTED_SCOPE), emptyList()
                )
            } returns claims
        }
        coEvery { tokenRepository.save(any()) } answers { firstArg<AuthenticationTokenEntity>() }
        every { tokenMapper.toEncodedAuthenticationToken(any(), any()) } returns mockk()

        val builder = JWTClaimsSet.Builder()
        coEvery { jwtManager.create(JwtManager.ACCESS_KEY, any(), any()) } answers {
            arg<JWTClaimsSet.Builder.() -> Unit>(2)(builder)
            "encoded-access-token"
        }
        return builder
    }

    private companion object {

        const val AUDIENCE_ID = "storefront"
        const val TOKEN_AUDIENCE = "https://api.example.com"
        const val CONSENTED_SCOPE = "profile"
    }
}
