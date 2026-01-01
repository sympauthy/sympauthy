package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.validationcode.ValidationCodeManager
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.code.ValidationCodeReason.EMAIL_CLAIM
import com.sympauthy.business.model.code.ValidationCodeReason.PHONE_NUMBER_CLAIM
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional

/**
 * Component in charge of validating the claim collected during the authorization flow.
 */
@Singleton
open class AuthorizationFlowClaimValidationManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val validationCodeManager: ValidationCodeManager,
) {

    /**
     * List of all [ValidationCodeReason] why this manager can send validation code to the user.
     * The list also contain reasons which this authorization server is not able to send a validation code for.
     */
    val validationCodeReasons: List<ValidationCodeReason>
        get() = listOf(EMAIL_CLAIM, PHONE_NUMBER_CLAIM)

    /**
     * Return the claim validated by the [reason], null if the [reason] actually does not validate a claim.
     */
    fun getClaimValidatedBy(reason: ValidationCodeReason): Claim? {
        val claimId = when (reason) {
            EMAIL_CLAIM -> EMAIL_CLAIM.media.claim
            PHONE_NUMBER_CLAIM -> PHONE_NUMBER_CLAIM.media.claim
            else -> null
        }
        return claimId?.let(claimManager::findById)
    }

    /**
     * Return the list of reason why the authorization server must send validation code to the user.
     *
     * The list will only contain reason which this authorization server is able to send a validation code for.
     * ex. the authorization server cannot verify an email if there is no email sending solution configured.
     */
    fun getReasonsToSendValidationCode(
        collectedClaims: List<CollectedClaim>
    ): List<ValidationCodeReason> {
        return getUnfilteredReasonsToSendValidationCode(collectedClaims)
            .filter { validationCodeManager.canSendValidationCodeForReason(it) }
    }

    /**
     * Return the list of reason why the authorization server must send validation code to the user.
     *
     * The list may contain [ValidationCodeReason] which this authorization server is not able to send a validation code
     * for.
     */
    internal fun getUnfilteredReasonsToSendValidationCode(
        collectedClaims: List<CollectedClaim>
    ): List<ValidationCodeReason> {
        return validationCodeReasons.mapNotNull { reason ->
            getClaimValidatedBy(reason)?.let { claim ->
                val collectedClaim = collectedClaims.firstOrNull { it.claim.id == claim.id }
                if (collectedClaim?.verified != true) reason else null
            }
        }
    }

    /**
     * Return the list of [ValidationCodeMedia] the authorization server must send validation code to,
     * in order to validate of the [collectedClaims].
     */
    fun getMediaToSendValidationCodeTo(
        collectedClaims: List<CollectedClaim>
    ): List<ValidationCodeMedia> {
        return getReasonsToSendValidationCode(collectedClaims)
            .map { it.media }
            .distinct()
    }

    /**
     * Send a [ValidationCode] to the [user] using the provided [media] if necessary.
     *
     * This method will:
     * - If there is no claim that require a validation, then this method returns null.
     * - If a [ValidationCode] has been previously sent, then this method does not send a new code
     * and return the latest generated [ValidationCode] (even if it is expired).
     * - Otherwise, send a validation code to the [user] using the provided [media] to validate claims collected by this
     * authorization server.
     */
    @Transactional
    open suspend fun getOrSendValidationCode(
        authorizeAttempt: AuthorizeAttempt,
        user: User,
        media: ValidationCodeMedia
    ): ValidationCode? {
        val collectedClaims = collectedClaimManager.findClaimsReadableByAttempt(
            authorizeAttempt = authorizeAttempt
        )

        val reasons = getReasonsToSendValidationCode(
            collectedClaims = collectedClaims
        ).filter { it.media == media }
        if (reasons.isEmpty()) return null

        val existingCode = validationCodeManager.findLatestCodeSentByMediaDuringAttempt(
            authorizeAttempt = authorizeAttempt,
            media = media,
            includesExpired = true
        )
        return if (existingCode == null) {
            validationCodeManager.queueRequiredValidationCodes(
                user = user,
                authorizeAttempt = authorizeAttempt,
                reasons = reasons,
                collectedClaims = collectedClaims
            ).firstOrNull()
        } else existingCode
    }

    /**
     * Resend the codes that were previously sent to this [user] during the [authorizeAttempt].
     * Return the list of codes that have been resent.
     *
     * For a code to be resent using a given media, all previous code sent using this media must have passed
     * their [ValidationCode.resendDate]. A null [ValidationCode.resendDate] correspond to a never expiring code.
     */
    @Transactional
    open suspend fun resendValidationCode(
        authorizeAttempt: AuthorizeAttempt,
        user: User,
        media: ValidationCodeMedia
    ): ResendResult {
        val existingCode = validationCodeManager.findLatestCodeSentByMediaDuringAttempt(
            authorizeAttempt = authorizeAttempt,
            media = media,
            includesExpired = true,
        )
        if (existingCode == null || !validationCodeManager.canBeRefreshed(existingCode)) {
            return ResendResult(
                resent = false,
                validationCode = existingCode,
            )
        }

        val collectedClaims = collectedClaimManager.findClaimsReadableByAttempt(
            authorizeAttempt = authorizeAttempt,
        )

        val result = validationCodeManager.refreshAndQueueValidationCode(
            user = user,
            authorizeAttempt = authorizeAttempt,
            collectedClaims = collectedClaims,
            validationCode = existingCode,
        )
        return ResendResult(
            resent = result.refreshed,
            validationCode = result.validationCode,
        )
    }

    @Transactional
    open suspend fun validateClaimsByCode(
        authorizeAttempt: AuthorizeAttempt,
        media: ValidationCodeMedia,
        code: String
    ) {
        val validationCodes = findCodesSentDuringAttempt(
            authorizeAttempt = authorizeAttempt,
            media = media
        )

        val matchingValidationCode = validationCodes.firstOrNull { it.code == code }
        if (matchingValidationCode == null) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.claim_validation.invalid_code"
            )
        }
        if (matchingValidationCode.expired) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.claim_validation.expired_code"
            )
        }

        val claims = matchingValidationCode.reasons.mapNotNull(this::getClaimValidatedBy)
        collectedClaimManager.validateClaims(
            userId = authorizeAttempt.userId!!,
            claims = claims
        )
    }

    /**
     * Return the code we have sent to the user to validate a claim during the [authorizeAttempt].
     * If a [media] is provided, it will only return codes send using this [media].
     *
     * This method ignores return codes that have been sent for other reason like resetting user password, etc.
     */
    internal suspend fun findCodesSentDuringAttempt(
        authorizeAttempt: AuthorizeAttempt,
        media: ValidationCodeMedia? = null,
    ): List<ValidationCode> {
        val codes = validationCodeManager.findCodeForReasonsDuringAttempt(
            authorizeAttempt = authorizeAttempt,
            reasons = validationCodeReasons,
            includesExpired = true
        )
        return if (media != null) {
            codes.filter { it.media == media }
        } else {
            codes
        }
    }

    data class ResendResult(
        /**
         * True if a new [ValidationCode] has been generated and sent to the user.
         */
        val resent: Boolean,
        /**
         * The new [ValidationCode] generated and sent to the user if [resent] is true.
         * Otherwise, the previous [ValidationCode] sent to the user if it exists.
         */
        val validationCode: ValidationCode?
    )
}
