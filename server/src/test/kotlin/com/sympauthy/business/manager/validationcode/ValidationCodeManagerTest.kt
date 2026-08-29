package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.manager.util.assertThrowsLocalizedException
import com.sympauthy.business.mapper.ValidationCodeMapper
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.code.ValidationCodeReason.EMAIL_CLAIM
import com.sympauthy.business.model.code.ValidationCodeReason.PHONE_NUMBER_CLAIM
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.ValidationCodeEntity
import com.sympauthy.data.repository.ValidationCodeRepository
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ValidationCodeManagerTest {

    @MockK
    lateinit var validationCodeGenerator: ValidationCodeGenerator

    @MockK
    lateinit var validationCodeRepository: ValidationCodeRepository

    val senders: MutableList<ValidationCodeMediaSender> = mutableListOf()

    @MockK
    lateinit var validationCodeMapper: ValidationCodeMapper

    @SpyK
    @InjectMockKs
    lateinit var manager: ValidationCodeManager

    @BeforeEach
    fun setUp() {
        senders.clear()
    }

    @Test
    fun `canSendValidationCodeForReason - Can send if a sender is available for the reason`() {
        val sender = mockk<ValidationCodeMediaSender> {
            every { media } returns EMAIL
            every { enabled } returns true
        }
        senders.add(sender)

        assertTrue(manager.canSendValidationCodeForReason(EMAIL_CLAIM))
    }

    @Test
    fun `canSendValidationCodeForReason - Cannot send if no sender is available for the reason`() {
        assertFalse(manager.canSendValidationCodeForReason(EMAIL_CLAIM))
    }

    @Test
    fun `getSenderByMediaMap - Return corresponding sender for media`() {
        val sender = mockk<ValidationCodeMediaSender> {
            every { media } returns EMAIL
            every { enabled } returns true
        }
        val emailClaim = mockk<Claim> {
            every { id } returns EMAIL.claim
        }
        val collectedEmailClaim = mockk<CollectedClaim> {
            every { claim } returns emailClaim
        }

        senders.add(sender)

        val result = manager.getSenderByMediaMap(
            medias = setOf(EMAIL),
            collectedClaims = listOf(collectedEmailClaim)
        )

        val resultSender = result[EMAIL]
        assertNotNull(resultSender)
        assertSame(EMAIL, resultSender?.media)
        assertSame(collectedEmailClaim, resultSender?.collectedClaim)
        assertSame(sender, resultSender?.sender)
    }

    @Test
    fun `getSenderByMediaMap - Throws exception if sender is missing`() {
        val collectedEmailClaim = mockk<CollectedClaim> {}

        assertThrowsLocalizedException("validationcode.missing_sender") {
            manager.getSenderByMediaMap(
                medias = setOf(EMAIL),
                collectedClaims = listOf(collectedEmailClaim)
            )
        }
    }

    @Test
    fun `getSenderByMediaMap - Throws exception if collected claim is missing`() {
        val sender = mockk<ValidationCodeMediaSender> {
            every { media } returns EMAIL
            every { enabled } returns true
        }

        senders.add(sender)

        assertThrowsLocalizedException("validationcode.missing_claim") {
            manager.getSenderByMediaMap(
                medias = setOf(EMAIL),
                collectedClaims = listOf()
            )
        }
    }

    @Test
    fun `queueRequiredValidationCodes - Generate codes for each reasons and send them through associated medias`() =
        runTest {
            val reason = EMAIL_CLAIM
            val mockUserId = UUID.randomUUID()
            val user = mockk<User> {
                every { id } returns mockUserId
            }
            val session = mockk<OnGoingInteractiveFlowSession> {
                every { userId } returns mockUserId
            }
            val collectedClaim = mockk<CollectedClaim> {
                every { userId } returns mockUserId
            }
            val sender = mockk<ValidationCodeMediaSender>()
            val senderClaimTuple = ValidationCodeManager.SenderClaimTuple(
                media = EMAIL,
                collectedClaim = collectedClaim,
                sender = sender
            )
            val validationCode = mockk<ValidationCode> {
                every { media } returns EMAIL
            }

            every {
                manager.getSenderByMediaMap(
                    medias = setOf(EMAIL),
                    collectedClaims = listOf(collectedClaim)
                )
            } returns mapOf(EMAIL to senderClaimTuple)
            coEvery {
                validationCodeGenerator.generateValidationCode(
                    user = user,
                    session = session,
                    media = EMAIL,
                    reasons = listOf(reason)
                )
            } returns validationCode
            coEvery {
                sender.sendValidationCode(
                    user = user,
                    collectedClaim = collectedClaim,
                    validationCode = validationCode
                )
            } returns Unit

            val result = manager.queueRequiredValidationCodes(
                user = user,
                session = session,
                collectedClaims = listOf(collectedClaim),
                reasons = listOf(reason)
            )

            assertEquals(1, result.size)
            assertTrue(result.contains(validationCode))
        }

    @Test
    fun `findCodeForReasonsDuringSession - Does not return non-matching reasons or expired`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<InteractiveFlowSession> {
            every { id } returns sessionId
        }
        val expiredEntity = mockk<ValidationCodeEntity>()
        val nonMatchingEntity = mockk<ValidationCodeEntity>()
        val matchingEntity = mockk<ValidationCodeEntity>()
        val expiredCode = mockk<ValidationCode> {
            every { reasons } returns listOf(EMAIL_CLAIM)
            every { expired } returns true
        }
        val matchingCode = mockk<ValidationCode> {
            every { reasons } returns listOf(EMAIL_CLAIM)
            every { expired } returns false
        }
        val nonMatchingCode = mockk<ValidationCode> { every { reasons } returns listOf(PHONE_NUMBER_CLAIM) }

        coEvery { validationCodeRepository.findBySessionId(sessionId) } returns listOf(
            expiredEntity, nonMatchingEntity, matchingEntity
        )
        every { validationCodeMapper.toValidationCode(expiredEntity) } returns expiredCode
        every { validationCodeMapper.toValidationCode(matchingEntity) } returns matchingCode
        every { validationCodeMapper.toValidationCode(nonMatchingEntity) } returns nonMatchingCode

        val result = manager.findCodeForReasonsDuringSession(
            session = session,
            reasons = listOf(EMAIL_CLAIM),
            includesExpired = false
        )

        assertEquals(1, result.size)
        assertSame(matchingCode, result.getOrNull(0))
    }

    @Test
    fun `findCodeForReasonsDuringSession - Does not return non-matching reasons`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<InteractiveFlowSession> {
            every { id } returns sessionId
        }
        val expiredEntity = mockk<ValidationCodeEntity>()
        val nonMatchingEntity = mockk<ValidationCodeEntity>()
        val matchingEntity = mockk<ValidationCodeEntity>()
        // Expired codes are asked for, so nothing is filtered on the expiry.
        val expiredCode = mockk<ValidationCode> { every { reasons } returns listOf(EMAIL_CLAIM) }
        val matchingCode = mockk<ValidationCode> { every { reasons } returns listOf(EMAIL_CLAIM) }
        val nonMatchingCode = mockk<ValidationCode> { every { reasons } returns listOf(PHONE_NUMBER_CLAIM) }

        coEvery { validationCodeRepository.findBySessionId(sessionId) } returns listOf(
            expiredEntity, nonMatchingEntity, matchingEntity
        )
        every { validationCodeMapper.toValidationCode(expiredEntity) } returns expiredCode
        every { validationCodeMapper.toValidationCode(matchingEntity) } returns matchingCode
        every { validationCodeMapper.toValidationCode(nonMatchingEntity) } returns nonMatchingCode

        val result = manager.findCodeForReasonsDuringSession(
            session = session,
            reasons = listOf(EMAIL_CLAIM),
            includesExpired = true
        )

        assertEquals(2, result.size)
        assertSame(expiredCode, result.getOrNull(0))
        assertSame(matchingCode, result.getOrNull(1))
    }

    @Test
    fun `findCodeForReasonsDuringSession - Does not include expired`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<InteractiveFlowSession> {
            every { id } returns sessionId
        }
        val media = EMAIL
        val validCodeEntity = mockk<ValidationCodeEntity>()
        val validCode = mockk<ValidationCode> {
            every { expired } returns false
        }
        val expiredCodeEntity = mockk<ValidationCodeEntity>()
        val expiredCode = mockk<ValidationCode> {
            every { expired } returns true
        }

        coEvery { validationCodeRepository.findBySessionIdAndMedia(sessionId, media.name) } returns listOf(
            validCodeEntity, expiredCodeEntity
        )
        every { validationCodeMapper.toValidationCode(validCodeEntity) } returns validCode
        every { validationCodeMapper.toValidationCode(expiredCodeEntity) } returns expiredCode

        val result = manager.findCodeSentByMediaDuringSession(
            session = session,
            media = media,
            includesExpired = false,
        )

        assertEquals(1, result.size)
        assertSame(validCode, result.getOrNull(0))
    }

    @Test
    fun `refreshAndQueueValidationCode - Do nothing if validation is not refreshable`() = runTest {
        val mockUserId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns mockUserId
        }
        val mockSessionId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { id } returns mockSessionId
            every { userId } returns mockUserId
        }
        val validationCode = mockk<ValidationCode> {
            every { sessionId } returns mockSessionId
        }

        every { manager.canBeRefreshed(validationCode) } returns false

        val result = manager.refreshAndQueueValidationCode(
            user = user,
            session = session,
            collectedClaims = emptyList(),
            validationCode = validationCode,
        )

        assertFalse(result.refreshed)
        assertSame(validationCode, result.validationCode)
        coVerify(exactly = 0) { validationCodeGenerator.generateValidationCode(mockk(), mockk(), mockk(), mockk()) }
    }

    @Test
    fun `canBeRefreshed - Returns true if validation code is expired`() {
        // An expired code can be refreshed whatever its resend date says.
        val validationCode = mockk<ValidationCode> { every { expired } returns true }

        assertTrue(manager.canBeRefreshed(validationCode))
    }

    @Test
    fun `canBeRefreshed - Returns true if resendDate is in the past`() {
        val validationCode = mockk<ValidationCode> {
            every { expired } returns false
            every { resendDate } returns LocalDateTime.now().minusMinutes(1)
        }

        assertTrue(manager.canBeRefreshed(validationCode))
    }

    @Test
    fun `canBeRefreshed - Returns false if resendDate is in the future`() {
        val validationCode = mockk<ValidationCode> {
            every { expired } returns false
            every { resendDate } returns LocalDateTime.now().plusMinutes(1)
        }

        assertFalse(manager.canBeRefreshed(validationCode))
    }

    @Test
    fun `canBeRefreshed - Returns false if resendDate is null and not expired`() {
        val validationCode = mockk<ValidationCode> {
            every { expired } returns false
            every { resendDate } returns null
        }

        assertFalse(manager.canBeRefreshed(validationCode))
    }
}
