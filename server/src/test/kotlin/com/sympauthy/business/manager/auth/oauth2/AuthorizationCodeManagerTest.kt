package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.AuthorizationCodeMapper
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.micronaut.data.exceptions.DataIntegrityViolationException
import io.micronaut.data.exceptions.EntityExistsException
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

/**
 * The one code a session may hand out, and the replay of the request that already took it.
 *
 * The primary key over `session_id` is what refuses the second code, and the refusal reaches the manager
 * through layers free to wrap it — so the cases here drive it both wrapped and bare, and drive the other
 * way the write can be refused to prove it is not answered as a replay.
 */
@ExtendWith(MockKExtension::class)
class AuthorizationCodeManagerTest {

    private val codeRepository = mockk<AuthorizationCodeRepository>()
    private val codeMapper = mockk<AuthorizationCodeMapper>()
    private val randomGenerator = mockk<RandomGenerator>()
    private val manager = AuthorizationCodeManager(codeRepository, codeMapper, randomGenerator)

    private val expirationDate = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun session(): InteractiveFlowSession = mockk {
        every { id } returns SESSION_ID
        every { this@mockk.expirationDate } returns this@AuthorizationCodeManagerTest.expirationDate
    }

    @Test
    fun `generateCode - Writes the code against the session and answers what the row became`() = runTest {
        val code = mockk<AuthorizationCode>()
        val written = slot<AuthorizationCodeEntity>()
        every { randomGenerator.generateAndEncodeToHex(any()) } returns "0123456789abcdef"
        coEvery { codeRepository.save(capture(written)) } answers { written.captured }
        every { codeMapper.toAuthorizationCode(any()) } returns code

        assertSame(code, manager.generateCode(session()))

        assertEquals(SESSION_ID, written.captured.sessionId)
        assertEquals("0123456789abcdef", written.captured.code)
        assertEquals(expirationDate, written.captured.expirationDate)
    }

    @Test
    fun `generateCode - Answers a session that already took its code with a replay`() = runTest {
        every { randomGenerator.generateAndEncodeToHex(any()) } returns "0123456789abcdef"
        coEvery { codeRepository.save(any()) } throws EntityExistsException(
            "duplicate key", R2dbcDataIntegrityViolationException("23505")
        )

        val exception = assertThrows<OAuth2Exception> { manager.generateCode(session()) }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("code.already_generated", exception.detailsId)
        assertEquals("description.oauth2.replay", exception.descriptionId)
    }

    /** The shape a transaction interceptor or a coroutine bridge wrapping the refusal leaves it in. */
    @Test
    fun `generateCode - Answers a replay for a refusal that reached it wrapped`() = runTest {
        every { randomGenerator.generateAndEncodeToHex(any()) } returns "0123456789abcdef"
        coEvery { codeRepository.save(any()) } throws IllegalStateException(
            "transaction failed", EntityExistsException("duplicate key")
        )

        val exception = assertThrows<OAuth2Exception> { manager.generateCode(session()) }

        assertEquals("code.already_generated", exception.detailsId)
    }

    /**
     * The foreign key over a session that is gone refuses the write too, and it is this server writing a
     * row it should not have — telling the client it already took its code would send them to look for a
     * code nobody ever issued.
     */
    @Test
    fun `generateCode - Lets the refusal of a session that is gone travel as it is`() = runTest {
        every { randomGenerator.generateAndEncodeToHex(any()) } returns "0123456789abcdef"
        coEvery { codeRepository.save(any()) } throws DataIntegrityViolationException(
            "foreign key", R2dbcDataIntegrityViolationException("23503")
        )

        assertThrows<DataIntegrityViolationException> { manager.generateCode(session()) }
    }

    @Test
    fun `generateCode - Lets a failure nothing refused travel as it is`() = runTest {
        every { randomGenerator.generateAndEncodeToHex(any()) } returns "0123456789abcdef"
        coEvery { codeRepository.save(any()) } throws IllegalStateException("connection closed")

        assertThrows<IllegalStateException> { manager.generateCode(session()) }
    }

    private companion object {
        val SESSION_ID: UUID = UUID.randomUUID()
    }
}
