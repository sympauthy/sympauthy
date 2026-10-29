package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.mapper.AuthorizationCodeMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.config.model.AuthorizationCodeConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.monitoring.telemetry.TokenMetrics
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * The one code an authorization hands out under its grant, what an exchange checks of it before spending
 * it, and the one exchange that spends it.
 */
@ExtendWith(MockKExtension::class)
class AuthorizationCodeManagerTest {

    private val codeRepository = mockk<AuthorizationCodeRepository>()
    private val codeMapper = mockk<AuthorizationCodeMapper>()
    private val randomGenerator = mockk<RandomGenerator>()

    /**
     * The real manager behind a spy: whether a grant was opened for the client presenting it is a function of
     * the two alone, so the scenarios run it rather than stub an answer to it.
     */
    private val grantManager =
        spyk(GrantManager(mockk(), mockk(), mockk(), mockk(), mockk(), TokenMetrics(OpenTelemetry.noop())))
    private val authConfig = EnabledAuthConfig(
        issuer = "https://auth.example.com",
        token = mockk(),
        authorizationCode = AuthorizationCodeConfig(expiration = Duration.ofMinutes(1)),
        interactiveFlow = mockk(),
        grant = mockk(),
        identifierClaims = emptyList(),
        userMergingEnabled = false,
        byPassword = mockk()
    )
    private val manager = AuthorizationCodeManager(
        codeRepository, codeMapper, grantManager, randomGenerator, authConfig
    )

    private fun sessionById(): InteractiveFlowSession = mockk { every { id } returns SESSION_ID }

    private fun oauth2(): InteractiveFlowSessionOAuth2 = mockk {
        every { codeChallenge } returns CODE_CHALLENGE
        every { codeChallengeMethod } returns CodeChallengeMethod.S256
        every { nonce } returns NONCE
    }

    private fun grant(): Grant = mockk { every { id } returns GRANT_ID }

    private fun client(): Client = mockk {
        every { id } returns CLIENT_ID
        every { audience.id } returns AUDIENCE_ID
    }

    private fun code(expirationDate: LocalDateTime = LocalDateTime.now().plusMinutes(1)) = AuthorizationCode(
        grantId = GRANT_ID,
        code = "0123456789abcdef",
        codeChallenge = CODE_CHALLENGE,
        codeChallengeMethod = CodeChallengeMethod.S256,
        nonce = NONCE,
        creationDate = expirationDate.minusMinutes(1),
        expirationDate = expirationDate
    )

    @Test
    fun `generateCode - Writes the code under the grant and answers what the row became`() = runTest {
        val code = mockk<AuthorizationCode>()
        val written = slot<AuthorizationCodeEntity>()
        every {
            randomGenerator.generateAndEncodeToHex(AuthorizationCodeManager.CODE_LENGTH_IN_BYTES)
        } returns "0123456789abcdef"
        coEvery { codeRepository.save(capture(written)) } answers { written.captured }
        every { codeMapper.toAuthorizationCode(any()) } returns code

        assertSame(code, manager.generateCode(oauth2(), grant()))

        assertEquals(GRANT_ID, written.captured.grantId)
        assertEquals("0123456789abcdef", written.captured.code)
        assertEquals(CODE_CHALLENGE, written.captured.codeChallenge)
        assertEquals("S256", written.captured.codeChallengeMethod)
        assertEquals(NONCE, written.captured.nonce)
    }

    @Test
    fun `generateCode - Gives the code a lifetime counted from when it is issued`() = runTest {
        val written = slot<AuthorizationCodeEntity>()
        every { randomGenerator.generateAndEncodeToHex(any()) } returns "0123456789abcdef"
        coEvery { codeRepository.save(capture(written)) } answers { written.captured }
        every { codeMapper.toAuthorizationCode(any()) } returns mockk()

        manager.generateCode(oauth2(), grant())

        assertEquals(written.captured.creationDate.plusMinutes(1), written.captured.expirationDate)
    }

    @Test
    fun `generateCode - Refuses an authorization request holding no challenge as the server failing`() = runTest {
        val oauth2 = mockk<InteractiveFlowSessionOAuth2> {
            every { sessionId } returns SESSION_ID
            every { codeChallenge } returns null
            every { codeChallengeMethod } returns null
        }

        val exception = assertThrows<BusinessException> { manager.generateCode(oauth2, mockk()) }

        assertEquals("code.missing_code_challenge", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
        coVerify(exactly = 0) { codeRepository.save(any()) }
    }

    @Test
    fun `findByCodeOrNull - Answers the code holding that value`() = runTest {
        val entity = mockk<AuthorizationCodeEntity>()
        val code = mockk<AuthorizationCode>()
        coEvery { codeRepository.findByCode("0123456789abcdef") } returns entity
        every { codeMapper.toAuthorizationCode(entity) } returns code

        assertSame(code, manager.findByCodeOrNull("0123456789abcdef"))
    }

    @Test
    fun `findByCodeOrNull - Answers null where no code holds that value`() = runTest {
        coEvery { codeRepository.findByCode("0123456789abcdef") } returns null

        assertNull(manager.findByCodeOrNull("0123456789abcdef"))
    }

    @Test
    fun `fetchCode - Answers the code the session completed with`() = runTest {
        val entity = mockk<AuthorizationCodeEntity>()
        val code = mockk<AuthorizationCode>()
        coEvery { codeRepository.findBySessionId(SESSION_ID) } returns entity
        every { codeMapper.toAuthorizationCode(entity) } returns code

        assertSame(code, manager.fetchCode(sessionById()))
    }

    @Test
    fun `fetchCode - Refuses a session holding no code as the server failing`() = runTest {
        coEvery { codeRepository.findBySessionId(SESSION_ID) } returns null

        val exception = assertThrows<BusinessException> { manager.fetchCode(sessionById()) }

        assertEquals("code.missing", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `fetchCodeOrNull - Answers null for a session holding no code`() = runTest {
        coEvery { codeRepository.findBySessionId(SESSION_ID) } returns null

        assertNull(manager.fetchCodeOrNull(sessionById()))
    }

    private fun grantOpenedFor(clientId: String, audienceId: String = AUDIENCE_ID): Grant = mockk {
        every { this@mockk.clientId } returns clientId
        every { this@mockk.audienceId } returns audienceId
    }

    @Test
    fun `checkCanExchange - Answers the code and the grant it names`() = runTest {
        val code = code()
        val grant = grantOpenedFor(CLIENT_ID)
        coEvery { grantManager.findByIdOrNull(code.grantId) } returns grant

        val (checkedCode, checkedGrant) = manager.checkCanExchange(code, client())

        assertSame(code, checkedCode)
        assertSame(grant, checkedGrant)
    }

    @Test
    fun `checkCanExchange - Refuses a code this server does not hold as expired`() = runTest {
        val exception = assertThrows<OAuth2Exception> { manager.checkCanExchange(null, mockk()) }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
    }

    /** The grant is left unread: reaching the assertion is the proof nothing past the expiry was checked. */
    @Test
    fun `checkCanExchange - Refuses a code past its lifetime as expired`() = runTest {
        val code = code(expirationDate = LocalDateTime.now().minusSeconds(1))

        val exception = assertThrows<OAuth2Exception> { manager.checkCanExchange(code, mockk()) }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanExchange - Refuses as expired a code whose grant was collected since it was read`() = runTest {
        val code = code()
        coEvery { grantManager.findByIdOrNull(code.grantId) } returns null

        val exception = assertThrows<OAuth2Exception> { manager.checkCanExchange(code, mockk()) }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanExchange - Refuses a code whose grant was opened for another client`() = runTest {
        val code = code()
        val grant = mockk<Grant> { every { clientId } returns "another-client" }
        coEvery { grantManager.findByIdOrNull(code.grantId) } returns grant

        val client = mockk<Client> { every { id } returns CLIENT_ID }

        val exception = assertThrows<OAuth2Exception> { manager.checkCanExchange(code, client) }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.mismatching_client", exception.detailsId)
    }

    @Test
    fun `checkCanExchange - Refuses a code whose client has moved to another audience`() = runTest {
        val code = code()
        coEvery {
            grantManager.findByIdOrNull(code.grantId)
        } returns grantOpenedFor(CLIENT_ID, audienceId = "another-audience")

        val exception = assertThrows<OAuth2Exception> { manager.checkCanExchange(code, client()) }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.mismatching_audience", exception.detailsId)
    }

    @Test
    fun `fetchGrant - Answers the grant the code names`() = runTest {
        val grant = mockk<Grant>()
        coEvery { grantManager.findByIdOrNull(GRANT_ID) } returns grant

        assertSame(grant, manager.fetchGrant(code()))
    }

    @Test
    fun `markExchanged - Answers true for the call that marked the code`() = runTest {
        coEvery { codeRepository.updateExchangeDate(GRANT_ID, any()) } returns 1

        assertTrue(manager.markExchanged(code()))
    }

    @Test
    fun `markExchanged - Answers false for a code a client already exchanged`() = runTest {
        coEvery { codeRepository.updateExchangeDate(GRANT_ID, any()) } returns 0

        assertFalse(manager.markExchanged(code()))
    }

    private companion object {
        val SESSION_ID: UUID = UUID.randomUUID()
        val GRANT_ID: UUID = UUID.randomUUID()
        const val CLIENT_ID = "client-id"
        const val AUDIENCE_ID = "audience-id"
        const val CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        const val NONCE = "the-nonce"
    }
}
