package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.mapper.ValidationCodeMapper
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.data.repository.ValidationCodeRepository
import com.sympauthy.exception.localizedExceptionOf
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.LocalDateTime

@Singleton
open class ValidationCodeManager(
    @Inject private val validationCodeGenerator: ValidationCodeGenerator,
    @Inject private val validationCodeRepository: ValidationCodeRepository,
    @Inject private val senders: List<ValidationCodeMediaSender>,
    @Inject private val validationCodeMapper: ValidationCodeMapper
) {

    /**
     * Return the list of existing [ValidationCode] generated during the [authorizeAttempt] for one of the
     * provided [reasons].
     */
    suspend fun findCodeForReasonsDuringAttempt(
        authorizeAttempt: AuthorizeAttempt,
        reasons: List<ValidationCodeReason>,
        includesExpired: Boolean = false
    ): List<ValidationCode> {
        var sequence = validationCodeRepository
            .findByAttemptId(attemptId = authorizeAttempt.id)
            .asSequence()
            .map(validationCodeMapper::toValidationCode)
            .filter { entity ->
                entity.reasons.any { reasons.contains(it) }
            }
        if (!includesExpired) {
            sequence = sequence.filterNot(ValidationCode::expired)
        }
        return sequence.toList()
    }

    /**
     * Return the list of [ValidationCode] generated during the [authorizeAttempt] and sent using the provided
     * [media].
     */
    suspend fun findCodeSentByMediaDuringAttempt(
        authorizeAttempt: AuthorizeAttempt,
        media: ValidationCodeMedia,
        includesExpired: Boolean = false
    ): List<ValidationCode> {
        var sequence = validationCodeRepository
            .findByAttemptIdAndMedia(
                attemptId = authorizeAttempt.id,
                media = media.name
            )
            .asSequence()
            .map(validationCodeMapper::toValidationCode)
        if (!includesExpired) {
            sequence = sequence.filterNot(ValidationCode::expired)
        }
        return sequence.toList()
    }

    /**
     * Return the latest [ValidationCode] generated during the [authorizeAttempt] and sent using the provided
     * [media].
     */
    internal suspend fun findLatestCodeSentByMediaDuringAttempt(
        authorizeAttempt: AuthorizeAttempt,
        media: ValidationCodeMedia,
        includesExpired: Boolean = false
    ): ValidationCode? {
        return findCodeSentByMediaDuringAttempt(
            authorizeAttempt = authorizeAttempt,
            media = media,
            includesExpired = includesExpired
        ).maxByOrNull(ValidationCode::creationDate)
    }

    /**
     * Return true if this authorization server can send validation code to the end-user for the provided [reason].
     * False otherwise.
     */
    fun canSendValidationCodeForReason(reason: ValidationCodeReason): Boolean {
        return senders.any { it.media == reason.media }
    }

    /**
     * Queue the sending of [ValidationCode] of the provided [reasons].
     * If multiple reasons shares the same [ValidationCodeMedia], we will send only one code for those reasons.
     */
    @Transactional
    open suspend fun queueRequiredValidationCodes(
        user: User,
        authorizeAttempt: OnGoingAuthorizeAttempt,
        collectedClaims: List<CollectedClaim>,
        reasons: List<ValidationCodeReason>
    ): List<ValidationCode> {
        if (user.id != authorizeAttempt.userId) {
            throw IllegalArgumentException("The user (${user.id}) does not match the one in the authorizeAttempt (${authorizeAttempt.userId}).")
        }
        if (collectedClaims.any { it.userId != user.id }) {
            throw IllegalArgumentException("One of the collectedClaims does not have a matching user (${user.id}).")
        }

        val reasonsByMediaMap = reasons.groupBy(ValidationCodeReason::media)

        val senderByMediaMap = getSenderByMediaMap(
            medias = reasonsByMediaMap.keys,
            collectedClaims = collectedClaims
        )

        val codes = reasonsByMediaMap.map { (media, reasons) ->
            validationCodeGenerator.generateValidationCode(
                user = user,
                media = media,
                reasons = reasons,
                authorizeAttempt = authorizeAttempt
            )
        }

        codes.forEach {
            val sender = senderByMediaMap[it.media]!! // !! because it should never happen.
            sender.sender.sendValidationCode(
                user = user,
                collectedClaim = sender.collectedClaim,
                validationCode = it
            )
        }

        return codes
    }

    /**
     * Queue the sending of a new [ValidationCode] to refresh the provided [validationCode].
     * The [RefreshResult] will contain the newly generated [ValidationCode].
     *
     * If the [validationCode] cannot be refreshed according to [canBeRefreshed] rules, then this method
     * will not generate and send a new [ValidationCode]. The [RefreshResult] will contain the provided
     * [validationCode].
     */
    open suspend fun refreshAndQueueValidationCode(
        user: User,
        authorizeAttempt: OnGoingAuthorizeAttempt,
        collectedClaims: List<CollectedClaim>,
        validationCode: ValidationCode
    ): RefreshResult {
        if (validationCode.attemptId != authorizeAttempt.id) {
            throw IllegalArgumentException("The authorizeAttempt (${authorizeAttempt.id}) does not match the one in the validationCode (${validationCode.attemptId}).")
        }
        if (user.id != authorizeAttempt.userId) {
            throw IllegalArgumentException("The user (${user.id}) does not match the one in the authorizeAttempt (${authorizeAttempt.userId}).")
        }
        if (collectedClaims.any { it.userId != user.id }) {
            throw IllegalArgumentException("One of the collectedClaims does not have a matching user (${user.id}).")
        }

        if (!canBeRefreshed(validationCode)) {
            return RefreshResult(
                refreshed = false,
                validationCode = validationCode,
            )
        }

        val newValidationCode = validationCodeGenerator.generateValidationCode(
            user = user,
            media = validationCode.media,
            reasons = validationCode.reasons,
            authorizeAttempt = authorizeAttempt
        )

        val senderByMediaMap = getSenderByMediaMap(
            medias = setOf(validationCode.media),
            collectedClaims = collectedClaims
        )

        val sender = senderByMediaMap[newValidationCode.media] ?: throw localizedExceptionOf(
            "validationcode.missing_sender",
            "media" to newValidationCode.code
        )
        sender.sender.sendValidationCode(
            user = user,
            collectedClaim = sender.collectedClaim,
            validationCode = newValidationCode
        )
        return RefreshResult(
            refreshed = true,
            validationCode = newValidationCode,
        )
    }

    /**
     * Return true if on a demand to refresh of the validation code, this server should generate a new validation code
     * or keep the provided [validationCode].
     */
    fun canBeRefreshed(validationCode: ValidationCode): Boolean {
        return validationCode.expired || validationCode.resendDate?.isBefore(LocalDateTime.now()) == true
    }

    internal fun getSenderByMediaMap(
        medias: Set<ValidationCodeMedia>,
        collectedClaims: List<CollectedClaim>
    ): Map<ValidationCodeMedia, SenderClaimTuple> {
        return medias.associateWith { media ->
            val sender = senders.firstOrNull { it.media == media }
                ?: throw localizedExceptionOf(
                    "validationcode.missing_sender",
                    "media" to media.name
                )
            val claim = collectedClaims.firstOrNull { it.claim.id == media.claim }
                ?: throw localizedExceptionOf(
                    "validationcode.missing_claim",
                    "media" to media.name,
                    "claim" to media.claim
                )
            SenderClaimTuple(
                media = media,
                sender = sender,
                collectedClaim = claim
            )
        }
    }

    /**
     * Delete existing validation codes.
     */
    suspend fun revokeValidationCodes(codes: List<ValidationCode>) {
        codes.mapNotNull(ValidationCode::id)
            .let { validationCodeRepository.deleteByIds(it) }
    }

    internal data class SenderClaimTuple(
        val media: ValidationCodeMedia,
        val sender: ValidationCodeMediaSender,
        val collectedClaim: CollectedClaim
    )

    data class RefreshResult(
        /**
         * True if a new [ValidationCode] has been generated and sent to the user.
         */
        val refreshed: Boolean,
        /**
         * The new [ValidationCode] generated and sent to the user if [refreshed] is true.
         * Otherwise,the [ValidationCode] provided to the [refreshAndQueueValidationCode] method.
         */
        val validationCode: ValidationCode
    )
}
