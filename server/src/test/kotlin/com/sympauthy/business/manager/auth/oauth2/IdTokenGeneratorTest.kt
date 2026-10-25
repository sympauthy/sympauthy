package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jwt.JWTClaimsSet
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ClaimValueValidator
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.oauth2.BuiltInGrantableScopeId
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.PersonalClaim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.BOOLEAN
import com.sympauthy.business.model.user.claim.ClaimDataType.EMAIL
import com.sympauthy.business.model.user.claim.ClaimDataType.DATE
import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.PHONE_NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
import com.sympauthy.business.model.user.claim.ClaimDataType.TIMEZONE
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.TokenConfig
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import io.micronaut.serde.ObjectMapper
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@ExtendWith(MockKExtension::class)
class IdTokenGeneratorTest {

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var generatedClaimsManager: GeneratedClaimsManager

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var tokenMapper: EncodedAuthenticationTokenMapper

    @MockK
    lateinit var uncheckedAdvancedConfig: EnabledAdvancedConfig

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    @InjectMockKs
    lateinit var generator: IdTokenGenerator

    private val savedEntity = slot<AuthenticationTokenEntity>()

    private val audience = Audience(id = AUDIENCE, tokenAudience = AUDIENCE)

    /** The doors the access token issued beside the id token comes through. */
    private val doors = mockk<ClaimAclTokenDoors>()

    private val readAudienceId = slot<String>()

    private val readRequestedClaims = slot<RequestedClaims>()

    private val valueValidator = ClaimValueValidator()

    private val valueMapper = ClaimValueMapper(ObjectMapper.getDefault())

    @Test
    fun `generateIdToken - EnabledClaim the hash of the access token it was issued beside`() = runTest {
        val userId = UUID.randomUUID()
        val accessToken = mockk<EncodedAuthenticationToken> {
            every { token } returns ACCESS_TOKEN
        }
        val claimsSet = issue(userId, accessToken)

        assertEquals("77QmUPtjPfzWtF2AnpK9RQ", claimsSet.getStringClaim("at_hash"))
    }

    @Test
    fun `generateIdToken - Record the token under its grant and the grant type that issued it`() = runTest {
        val grant = grant()

        issue(grant, grantType = GrantType.REFRESH_TOKEN)

        val entity = savedEntity.captured
        assertEquals(grant.id, entity.grantId)
        assertEquals("refresh_token", entity.grantType)
    }

    @Test
    fun `generateIdToken - Claim the moment the grant records the person proved a credential`() = runTest {
        val claimsSet = issue(grant())

        assertEquals(
            AUTHENTICATION_DATE.toEpochSecond(ZoneOffset.UTC),
            claimsSet.getLongClaim("auth_time")
        )
    }

    @Test
    fun `generateIdToken - Claim no auth_time where the grant records none`() = runTest {
        val claimsSet = issue(grant(authenticationDate = null))

        assertNull(claimsSet.getClaim("auth_time"))
    }

    @Test
    fun `generateIdToken - Claim the nonce of the authorization request`() = runTest {
        val claimsSet = issue(grant(), nonce = "nonce")

        assertEquals("nonce", claimsSet.getStringClaim("nonce"))
    }

    @Test
    fun `generateIdToken - Claim no nonce where none is given`() = runTest {
        val claimsSet = issue(grant(), grantType = GrantType.REFRESH_TOKEN)

        assertNull(claimsSet.getClaim("nonce"))
    }

    @Test
    fun `generateIdToken - Expires no later than the grant it is issued under`() = runTest {
        val grantExpirationDate = LocalDateTime.now().plusMinutes(1).withNano(0)

        val claimsSet = issue(grant(expirationDate = grantExpirationDate))

        assertEquals(grantExpirationDate, savedEntity.captured.expirationDate)
        assertEquals(Date.from(grantExpirationDate.toInstant(ZoneOffset.UTC)), claimsSet.expirationTime)
    }

    @Test
    fun `generateIdToken - Issue one where the grant carries openid`() = runTest {
        val grant = grant()
        stubGeneration(checkNotNull(grant.userId))

        val idToken = generator.generateIdToken(
            grant = grant,
            audience = audience,
            accessToken = mockk { every { token } returns ACCESS_TOKEN },
            doors = doors,
            grantType = GrantType.AUTHORIZATION_CODE
        )

        assertNotNull(idToken)
    }

    @Test
    fun `generateIdToken - Issue none where the grant does not carry openid`() = runTest {
        val idToken = generator.generateIdToken(
            grant = grant(grantedScopes = listOf(CONSENTED_SCOPE)),
            audience = audience,
            accessToken = mockk(),
            doors = doors,
            grantType = GrantType.REFRESH_TOKEN
        )

        assertNull(idToken)
    }

    @Test
    fun `generateIdToken - Issue none where the grant names no person`() = runTest {
        val idToken = generator.generateIdToken(
            grant = grant(userId = null),
            audience = audience,
            accessToken = mockk(),
            doors = doors,
            grantType = GrantType.CLIENT_CREDENTIALS
        )

        assertNull(idToken)
    }

    @Test
    fun `generateIdToken - Read the claims for the audience it is given`() = runTest {
        issue(grant())

        assertEquals(AUDIENCE, readAudienceId.captured)
    }

    @Test
    fun `generateIdToken - Read the claims the authorization behind the grant asked for by name`() = runTest {
        val requested = RequestedClaims(idTokenClaimIds = setOf("loyalty_tier"), userInfoClaimIds = emptySet())

        issue(grant(requestedClaims = requested))

        assertEquals(requested, readRequestedClaims.captured)
    }

    @Test
    fun `generateIdToken - EnabledClaim the verified companion beside the value it answers for`() = runTest {
        val claimsSet = issue(
            collected(claim("email", EMAIL, verifiedId = "email_verified"), "ada@example.com", verified = true)
        )

        assertEquals("ada@example.com", claimsSet.getClaim("email"))
        assertEquals(true, claimsSet.getClaim("email_verified"))
    }

    @Test
    fun `generateIdToken - EnabledClaim the verified companion as false where the value is unverified`() = runTest {
        val claimsSet = issue(
            collected(claim("email", EMAIL, verifiedId = "email_verified"), "ada@example.com", verified = null)
        )

        assertEquals(false, claimsSet.getClaim("email_verified"))
    }

    @Test
    fun `generateIdToken - EnabledClaim no verified companion where the value was not published`() = runTest {
        val claimsSet = issue(
            collected(claim("email", EMAIL, verifiedId = "email_verified"), value = null, verified = true)
        )

        assertFalse(claimsSet.claims.containsKey("email"))
        assertFalse(claimsSet.claims.containsKey("email_verified"))
    }

    @ParameterizedTest
    @EnumSource(ClaimDataType::class)
    fun `generateIdToken - EnabledClaim a value of every type as the type its claim declares`(
        dataType: ClaimDataType
    ) = runTest {
        val (submitted, published) = VALUES.getValue(dataType)
        val claim = claim("value", dataType)

        val claimsSet = issue(collected(claim, storedValueOf(claim, submitted)))

        assertEquals(published, claimsSet.getClaim("value"))
    }

    @Test
    fun `generateIdToken - EnabledClaim no value disagreeing with the type its claim declares`() = runTest {
        val claimsSet = issue(collected(claim("age", NUMBER), "forty-two"))

        assertFalse(claimsSet.claims.containsKey("age"))
    }

    @Test
    fun `generateIdToken - EnabledClaim every address component as a string whatever its type`() = runTest {
        val claimsSet = issue(
            collected(claim("street_address", STRING, ClaimGroup.ADDRESS), "5 Rue Ada"),
            collected(claim("locality", STRING, ClaimGroup.ADDRESS), "Brussels"),
            collected(claim("postal_code", NUMBER, ClaimGroup.ADDRESS), 1000L),
            collected(claim("country", STRING, ClaimGroup.ADDRESS), "Belgium")
        )

        val address = claimsSet.getJSONObjectClaim("address")
        assertEquals("1000", address["postal_code"])
        assertEquals("5 Rue Ada\nBrussels, 1000\nBelgium", address["formatted"])
    }

    @Test
    fun `generateIdToken - EnabledClaim no address where no component carries a value`() = runTest {
        val claimsSet = issue(collected(claim("locality", STRING, ClaimGroup.ADDRESS), value = null))

        assertFalse(claimsSet.claims.containsKey("address"))
    }

    private fun claim(
        id: String,
        dataType: ClaimDataType,
        group: ClaimGroup? = null,
        verifiedId: String? = null,
        publishedIn: Set<ClaimPublicationPlace> = ClaimPublicationPlace.entries.toSet()
    ) = PersonalClaim(
        id = id,
        verifiedId = verifiedId,
        dataType = dataType,
        group = group,
        required = false,
        allowedValues = null,
        audienceId = null,
        publishedIn = publishedIn,
        publishedInWhenRequested = emptySet(),
        acl = ClaimAcl(
            collectedInFlow = ClaimAclExpression("true"),
            readableByPerson = ClaimAclExpression("true"),
            writableByPerson = null,
            readableByClient = ClaimAclExpression("true"),
            writableByClient = null
        )
    )

    /**
     * The [submitted] text as the value a [claim] of that type holds by the time an id token is built:
     * validated, written to the column, and read back out of it.
     *
     * The validator and the mapper are the real ones rather than doubles because they are what the
     * assertion turns on. Neither is a collaborator of the subject — the value reaches it having already
     * been through both — and a double here would return whichever type the test imagined, which is the
     * assumption that let a whole claim type go missing from every id token.
     */
    private fun storedValueOf(claim: EnabledClaim, submitted: String): Any? {
        val validated = valueValidator.validateAndCleanValueForClaim(claim, submitted).get()
        return valueMapper.toBusiness(checkNotNull(valueMapper.toEntity(validated)), claim.dataType)
    }

    private fun collected(claim: EnabledClaim, value: Any?, verified: Boolean? = null) = CollectedClaim(
        userId = UUID.randomUUID(),
        claim = claim,
        value = value,
        verified = verified,
        collectionDate = LocalDateTime.now(),
        verificationDate = null
    )

    private suspend fun issue(vararg claims: CollectedClaim): JWTClaimsSet = issue(
        userId = UUID.randomUUID(),
        accessToken = mockk { every { token } returns ACCESS_TOKEN },
        claims = claims.toList()
    )

    @Suppress("LongParameterList")
    private fun grant(
        userId: UUID? = UUID.randomUUID(),
        grantedScopes: List<String> = listOf(BuiltInGrantableScopeId.OPENID),
        consentedScopes: List<String> = listOf(CONSENTED_SCOPE),
        authenticationDate: LocalDateTime? = AUTHENTICATION_DATE,
        requestedClaims: RequestedClaims = RequestedClaims.NONE,
        expirationDate: LocalDateTime? = null
    ) = Grant(
        id = UUID.randomUUID(),
        userId = userId,
        clientId = "client",
        audienceId = AUDIENCE,
        grantType = GrantType.AUTHORIZATION_CODE,
        grantedScopes = grantedScopes,
        grantedAt = null,
        grantedBy = null,
        consentedScopes = consentedScopes,
        consentedAt = null,
        consentedBy = null,
        clientScopes = emptyList(),
        authenticationDate = authenticationDate,
        requestedClaims = requestedClaims,
        sessionId = UUID.randomUUID(),
        actorGrantId = null,
        creationDate = LocalDateTime.now(),
        expirationDate = expirationDate,
        revokedAt = null,
        revokedBy = null,
        revokedById = null
    )

    private suspend fun issue(
        grant: Grant,
        grantType: GrantType = GrantType.AUTHORIZATION_CODE,
        nonce: String? = null
    ): JWTClaimsSet {
        val accessToken = mockk<EncodedAuthenticationToken> {
            every { token } returns ACCESS_TOKEN
        }
        val builder = stubGeneration(userId = checkNotNull(grant.userId))

        generator.generateIdToken(grant, audience, accessToken, doors, grantType, nonce)

        return builder.build()
    }

    private fun stubGeneration(
        userId: UUID,
        claims: List<CollectedClaim> = emptyList()
    ): JWTClaimsSet.Builder {
        every { uncheckedAdvancedConfig.publicJwtAlgorithm } returns JwtAlgorithm.RS256
        every { uncheckedAuthConfig.token } returns mockk<TokenConfig> {
            every { idExpiration } returns Duration.ofMinutes(5)
        }
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn(
                userId,
                capture(readAudienceId),
                ClaimPublicationPlace.ID_TOKEN,
                doors,
                capture(readRequestedClaims)
            )
        } returns claims
        coEvery { tokenRepository.save(capture(savedEntity)) } answers { firstArg<AuthenticationTokenEntity>() }
        every { generatedClaimsManager.computeSubject(userId) } returns userId.toString()
        every { tokenMapper.toEncodedAuthenticationToken(any(), any(), any()) } returns mockk()

        val builder = JWTClaimsSet.Builder()
        coEvery { jwtManager.create(JwtManager.PUBLIC_KEY, any(), any()) } answers {
            arg<JWTClaimsSet.Builder.() -> Unit>(2)(builder)
            "encoded-id-token"
        }
        return builder
    }

    private suspend fun issue(
        userId: UUID,
        accessToken: EncodedAuthenticationToken,
        claims: List<CollectedClaim> = emptyList()
    ): JWTClaimsSet {
        val builder = stubGeneration(userId, claims = claims)

        generator.generateIdToken(
            grant = grant(userId = userId, consentedScopes = emptyList()),
            audience = audience,
            accessToken = accessToken,
            doors = doors,
            grantType = GrantType.AUTHORIZATION_CODE
        )

        return builder.build()
    }

    private companion object {
        /**
         * Per type, a value as a form posts one — text, whatever the claim's type says — and what it is
         * claimed as once it has been through the validator and the mapper. A type absent here fails its
         * own case rather than going untested.
         */
        val VALUES = mapOf<ClaimDataType, Pair<String, Any>>(
            BOOLEAN to ("true" to true),
            DATE to ("1815-12-10" to "1815-12-10"),
            EMAIL to ("ada@example.com" to "ada@example.com"),
            NUMBER to ("42" to 42L),
            PHONE_NUMBER to ("+14155550100" to "+14155550100"),
            STRING to ("Ada" to "Ada"),
            TIMEZONE to ("Europe/London" to "Europe/London")
        )

        const val CONSENTED_SCOPE = "email"
        const val AUDIENCE = "storefront"
        const val ACCESS_TOKEN = "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"

        val AUTHENTICATION_DATE: LocalDateTime = LocalDateTime.of(2025, 3, 4, 9, 15, 0)
    }
}
