package com.sympauthy.business.manager.token

import com.nimbusds.jwt.JWTClaimsSet
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.PersonalClaim
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.TokenConfig
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
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

    @SpyK
    var customSpanTracer = CustomSpanTracer(OpenTelemetry.noop())

    @InjectMockKs
    lateinit var generator: AccessTokenGenerator

    private val readAudienceId = slot<String>()

    /** The doors the token being issued comes through. */
    private val doors = mockk<ClaimAclTokenDoors>()

    @Test
    fun `generateAccessToken - EnabledClaim the values the deployment publishes in the access token`() = runTest {
        val claimsSet = issue(
            collected(claim("loyalty_tier", ClaimDataType.STRING), "gold"),
            collected(claim("age", ClaimDataType.NUMBER), 42L)
        )

        assertEquals("gold", claimsSet.getStringClaim("loyalty_tier"))
        assertEquals(42L, claimsSet.getLongClaim("age"))
    }

    @Test
    fun `generateAccessToken - EnabledClaim the verified companion beside the value it answers for`() = runTest {
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
    fun `generateAccessToken - EnabledClaim the address object OpenID Connect defines`() = runTest {
        val claimsSet = issue(
            collected(claim("locality", ClaimDataType.STRING, ClaimGroup.ADDRESS), "Brussels"),
            collected(claim("country", ClaimDataType.STRING, ClaimGroup.ADDRESS), "Belgium")
        )

        val address = claimsSet.getJSONObjectClaim("address")
        assertEquals("Brussels", address["locality"])
        assertEquals("Brussels\nBelgium", address["formatted"])
    }

    @Test
    fun `generateAccessToken - EnabledClaim nothing over a member the token states about its authorization`() =
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
    fun `generateAccessToken - Claims nothing about a person under a client credentials grant`() = runTest {
        val builder = stubGeneration()

        generator.generateAccessToken(
            grant = grant(userId = null, grantType = GrantType.CLIENT_CREDENTIALS, clientScopes = listOf("users:read")),
            audience = audience(),
            doors = doors,
            grantType = GrantType.CLIENT_CREDENTIALS
        )

        val claimsSet = builder.build()
        assertEquals("client", claimsSet.subject)
        assertEquals("users:read", claimsSet.getStringClaim("scope"))
        assertNull(claimsSet.getClaim("loyalty_tier"))
        assertFalse(claimsSet.claims.containsKey("address"))
        assertFalse(claimsSet.claims.containsKey("act"))
    }

    @Test
    fun `generateAccessToken - States the acting client under a grant a token exchange opened`() = runTest {
        val builder = stubGeneration()

        generator.generateAccessToken(
            grant = grant(userId = null, grantType = GrantType.TOKEN_EXCHANGE, actorGrantId = UUID.randomUUID()),
            audience = audience(),
            doors = doors,
            grantType = GrantType.TOKEN_EXCHANGE
        )

        assertEquals(mapOf("sub" to "client"), builder.build().getJSONObjectClaim("act"))
    }

    @Test
    fun `generateAccessToken - Expires no later than the grant it is issued under`() = runTest {
        val grantExpirationDate = LocalDateTime.now().plusMinutes(1).withNano(0)
        val saved = slot<AuthenticationTokenEntity>()
        val builder = stubGeneration(saved = saved)

        generator.generateAccessToken(
            grant = grant(userId = null, expirationDate = grantExpirationDate),
            audience = audience(),
            doors = doors,
            grantType = GrantType.REFRESH_TOKEN
        )

        assertEquals(grantExpirationDate, saved.captured.expirationDate)
        assertEquals("refresh_token", saved.captured.grantType)
        assertEquals(
            Date.from(grantExpirationDate.toInstant(ZoneOffset.UTC)),
            builder.build().expirationTime
        )
    }

    private fun audience() = Audience(id = AUDIENCE_ID, tokenAudience = TOKEN_AUDIENCE)

    private fun claim(
        id: String,
        dataType: ClaimDataType,
        group: ClaimGroup? = null,
        verifiedId: String? = null
    ) = PersonalClaim(
        id = id,
        verifiedId = verifiedId,
        dataType = dataType,
        group = group,
        required = false,
        allowedValues = null,
        audienceId = null,
        publishedIn = setOf(ClaimPublicationPlace.ACCESS_TOKEN),
        publishedInWhenRequested = emptySet(),
        acl = ClaimAcl(
            collectedInFlow = ClaimAclExpression("true"),
            readableByPerson = ClaimAclExpression("true"),
            writableByPerson = null,
            readableByClient = ClaimAclExpression("true"),
            writableByClient = null
        )
    )

    private fun collected(claim: EnabledClaim, value: Any?, verified: Boolean? = null) = CollectedClaim(
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
            grant = grant(userId = userId, consentedScopes = listOf(CONSENTED_SCOPE)),
            audience = audience(),
            doors = doors,
            grantType = GrantType.AUTHORIZATION_CODE
        )

        return builder.build()
    }

    @Suppress("LongParameterList")
    private fun grant(
        userId: UUID?,
        grantType: GrantType = GrantType.AUTHORIZATION_CODE,
        consentedScopes: List<String> = emptyList(),
        clientScopes: List<String> = emptyList(),
        actorGrantId: UUID? = null,
        expirationDate: LocalDateTime? = null
    ) = Grant(
        id = UUID.randomUUID(),
        userId = userId,
        clientId = "client",
        audienceId = AUDIENCE_ID,
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
        creationDate = LocalDateTime.now(),
        expirationDate = expirationDate,
        revokedAt = null,
        revokedBy = null,
        revokedById = null
    )

    private fun stubGeneration(
        userId: UUID? = null,
        claims: List<CollectedClaim> = emptyList(),
        saved: CapturingSlot<AuthenticationTokenEntity> = slot()
    ): JWTClaimsSet.Builder {
        every { uncheckedAuthConfig.token } returns mockk<TokenConfig> {
            every { accessExpiration } returns Duration.ofMinutes(5)
        }
        userId?.let {
            coEvery {
                consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
                    it, capture(readAudienceId), ClaimPublicationPlace.ACCESS_TOKEN,
                    doors
                )
            } returns claims
        }
        coEvery { tokenRepository.save(capture(saved)) } answers { firstArg<AuthenticationTokenEntity>() }
        every { tokenMapper.toEncodedAuthenticationToken(any(), any(), any()) } returns mockk()

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
