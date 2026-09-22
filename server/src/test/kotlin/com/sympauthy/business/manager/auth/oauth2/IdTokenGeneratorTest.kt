package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jwt.JWTClaimsSet
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.ClaimValueValidator
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.BuiltInGrantableScopeId
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.BOOLEAN
import com.sympauthy.business.model.user.claim.ClaimDataType.EMAIL
import com.sympauthy.business.model.user.claim.ClaimDataType.DATE
import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.PHONE_NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
import com.sympauthy.business.model.user.claim.ClaimDataType.TIMEZONE
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
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

    private val readAudienceId = slot<String>()

    @Test
    fun `generateIdToken - Claim the hash of the access token it was issued beside`() = runTest {
        val userId = UUID.randomUUID()
        val accessToken = mockk<EncodedAuthenticationToken> {
            every { token } returns ACCESS_TOKEN
        }
        val claimsSet = issue(userId, accessToken)

        assertEquals("77QmUPtjPfzWtF2AnpK9RQ", claimsSet.getStringClaim("at_hash"))
    }

    @Test
    fun `generateIdToken - Carry the grant of the refresh token it is issued from`() = runTest {
        val sessionId = UUID.randomUUID()
        val refreshToken = mockRefreshToken(sessionId = sessionId)

        issue(refreshToken)

        val entity = savedEntity.captured
        assertEquals(refreshToken.userId, entity.userId)
        assertEquals("client", entity.clientId)
        assertEquals(listOf(BuiltInGrantableScopeId.OPENID), entity.grantedScopes.toList())
        assertEquals(listOf(CONSENTED_SCOPE), entity.consentedScopes.toList())
        assertEquals(sessionId, entity.sessionId)
        assertEquals("refresh_token", entity.grantType)
    }

    @Test
    fun `generateIdToken - Claim no nonce on a refresh`() = runTest {
        val claimsSet = issue(mockRefreshToken(sessionId = UUID.randomUUID()))

        assertNull(claimsSet.getClaim("nonce"))
    }

    @Test
    fun `generateIdToken - Issue one where the authorization granted openid`() = runTest {
        val userId = UUID.randomUUID()
        stubGeneration(userId)

        val idToken = generator.generateIdToken(
            oauth2 = oauth2(grantedScopes = listOf(BuiltInGrantableScopeId.OPENID)),
            userId = userId,
            audienceId = AUDIENCE,
            accessToken = mockk { every { token } returns ACCESS_TOKEN }
        )

        assertNotNull(idToken)
    }

    @Test
    fun `generateIdToken - Issue none where the authorization did not grant openid`() = runTest {
        val idToken = generator.generateIdToken(
            oauth2 = oauth2(grantedScopes = listOf(CONSENTED_SCOPE)),
            userId = UUID.randomUUID(),
            audienceId = AUDIENCE,
            accessToken = mockk()
        )

        assertNull(idToken)
    }

    @Test
    fun `generateIdToken - Issue none where the grant a refresh descends from did not carry openid`() = runTest {
        val refreshToken = mockRefreshToken(
            sessionId = UUID.randomUUID(),
            grantedScopes = listOf(CONSENTED_SCOPE)
        )

        assertNull(generator.generateIdToken(refreshToken, AUDIENCE, mockk()))
    }

    @Test
    fun `generateIdToken - Read the claims for the audience a code exchange names`() = runTest {
        val userId = UUID.randomUUID()
        stubGeneration(userId)

        generator.generateIdToken(
            oauth2 = oauth2(grantedScopes = listOf(BuiltInGrantableScopeId.OPENID)),
            userId = userId,
            audienceId = AUDIENCE,
            accessToken = mockk { every { token } returns ACCESS_TOKEN }
        )

        assertEquals(AUDIENCE, readAudienceId.captured)
    }

    @Test
    fun `generateIdToken - Read the claims for the audience a refresh names`() = runTest {
        issue(mockRefreshToken(sessionId = UUID.randomUUID()))

        assertEquals(AUDIENCE, readAudienceId.captured)
    }

    @Test
    fun `generateIdToken - Claim the verified companion beside the value it answers for`() = runTest {
        val claimsSet = issue(
            collected(claim("email", EMAIL, verifiedId = "email_verified"), "ada@example.com", verified = true)
        )

        assertEquals("ada@example.com", claimsSet.getClaim("email"))
        assertEquals(true, claimsSet.getClaim("email_verified"))
    }

    @Test
    fun `generateIdToken - Claim the verified companion as false where the value is unverified`() = runTest {
        val claimsSet = issue(
            collected(claim("email", EMAIL, verifiedId = "email_verified"), "ada@example.com", verified = null)
        )

        assertEquals(false, claimsSet.getClaim("email_verified"))
    }

    @Test
    fun `generateIdToken - Claim no verified companion where the value was not published`() = runTest {
        val claimsSet = issue(
            collected(claim("email", EMAIL, verifiedId = "email_verified"), value = null, verified = true)
        )

        assertFalse(claimsSet.claims.containsKey("email"))
        assertFalse(claimsSet.claims.containsKey("email_verified"))
    }

    @ParameterizedTest
    @EnumSource(ClaimDataType::class)
    fun `generateIdToken - Claim a value of every type as the type its claim declares`(
        dataType: ClaimDataType
    ) = runTest {
        val (submitted, published) = VALUES.getValue(dataType)
        val claim = claim("value", dataType)

        val claimsSet = issue(collected(claim, storedValueOf(claim, submitted)))

        assertEquals(published, claimsSet.getClaim("value"))
    }

    @Test
    fun `generateIdToken - Claim no value disagreeing with the type its claim declares`() = runTest {
        val claimsSet = issue(collected(claim("age", NUMBER), "forty-two"))

        assertFalse(claimsSet.claims.containsKey("age"))
    }

    @Test
    fun `generateIdToken - Claim every address component as a string whatever its type`() = runTest {
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
    fun `generateIdToken - Claim no address where no component carries a value`() = runTest {
        val claimsSet = issue(collected(claim("locality", STRING, ClaimGroup.ADDRESS), value = null))

        assertFalse(claimsSet.claims.containsKey("address"))
    }

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
        group = group,
        required = false,
        generated = false,
        userInputted = true,
        allowedValues = null,
        audienceId = null,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByUser = true,
                writableByUser = true,
                readableByClient = true,
                writableByClient = false
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList()
            )
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
    private fun storedValueOf(claim: Claim, submitted: String): Any? {
        val validated = ClaimValueValidator().validateAndCleanValueForClaim(claim, submitted).get()
        val valueMapper = ClaimValueMapper(ObjectMapper.getDefault())
        return valueMapper.toBusiness(checkNotNull(valueMapper.toEntity(validated)), claim.dataType)
    }

    private fun collected(claim: Claim, value: Any?, verified: Boolean? = null) = CollectedClaim(
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

    private fun oauth2(grantedScopes: List<String>) = InteractiveFlowSessionOAuth2(
        sessionId = UUID.randomUUID(),
        clientId = "client",
        redirectUri = "https://client.example.com/callback",
        requestedScopes = grantedScopes,
        grantedScopes = grantedScopes
    )

    private fun mockRefreshToken(
        sessionId: UUID,
        grantedScopes: List<String> = listOf(BuiltInGrantableScopeId.OPENID)
    ): AuthenticationToken {
        val id = UUID.randomUUID()
        val scopes = grantedScopes
        return mockk {
            every { userId } returns id
            every { clientId } returns "client"
            every { this@mockk.grantedScopes } returns scopes
            every { consentedScopes } returns listOf(CONSENTED_SCOPE)
            every { this@mockk.sessionId } returns sessionId
        }
    }

    private suspend fun issue(refreshToken: AuthenticationToken): JWTClaimsSet {
        val accessToken = mockk<EncodedAuthenticationToken> {
            every { token } returns ACCESS_TOKEN
        }
        val builder = stubGeneration(
            userId = checkNotNull(refreshToken.userId),
            consentedScopes = refreshToken.consentedScopes
        )

        generator.generateIdToken(refreshToken, AUDIENCE, accessToken)

        return builder.build()
    }

    private fun stubGeneration(
        userId: UUID,
        consentedScopes: List<String> = emptyList(),
        claims: List<CollectedClaim> = emptyList()
    ): JWTClaimsSet.Builder {
        every { uncheckedAdvancedConfig.publicJwtAlgorithm } returns JwtAlgorithm.RS256
        every { uncheckedAuthConfig.token } returns mockk<TokenConfig> {
            every { idExpiration } returns Duration.ofMinutes(5)
        }
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
                userId, capture(readAudienceId), consentedScopes, any()
            )
        } returns claims
        coEvery { tokenRepository.save(capture(savedEntity)) } answers { firstArg<AuthenticationTokenEntity>() }
        every { generatedClaimsManager.computeSubject(userId) } returns userId.toString()
        every { tokenMapper.toEncodedAuthenticationToken(any(), any()) } returns mockk()

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
            userId = userId,
            clientId = "client",
            audienceId = AUDIENCE,
            grantedScopes = listOf(BuiltInGrantableScopeId.OPENID),
            consentedScopes = emptyList(),
            sessionId = null,
            accessToken = accessToken,
            grantType = "authorization_code"
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
    }
}
