package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.manager.validationcode.ValidationCodeManager
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.code.ValidationCodeReason.EMAIL_CLAIM
import com.sympauthy.business.model.code.ValidationCodeReason.PHONE_NUMBER_CLAIM
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import jakarta.inject.Inject
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional

/**
 * Component in charge of validating the claim collected during an interactive auth flow.
 */
@Singleton
open class InteractiveAuthFlowSessionClaimValidationManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val validationCodeManager: ValidationCodeManager,
) {

    /**
     * List of all [ValidationCodeReason] why this manager can send validation code to the user.
     * The list also contains reasons which this authorization server is not able to send a validation code for.
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
        return claimId?.let(claimManager::findByIdOrNull)
    }

    /**
     * Return the list of reason why the authorization server must send validation code to the user.
     *
     * The list will only contain reason which this authorization server is able to send a validation code for.
     * ex. the authorization server cannot verify an email if there is no email sending solution configured.
     *
     * @param identifierClaims claims that identify the user, which the authorization server must always validate
     * regardless of consent.
     * @param consentedClaims claims the client has consent for during this interactive flow session.
     */
    fun getReasonsToSendValidationCode(
        identifierClaims: List<CollectedClaim>,
        consentedClaims: List<CollectedClaim>
    ): List<ValidationCodeReason> {
        return getUnfilteredReasonsToSendValidationCode(identifierClaims, consentedClaims)
            .filter { validationCodeManager.canSendValidationCodeForReason(it) }
    }

    /**
     * Return the list of reason why the authorization server must send validation code to the user.
     *
     * The list may contain [ValidationCodeReason] which this authorization server is not able to send a validation code
     * for.
     *
     * @param identifierClaims claims that identify the user, which the authorization server must always validate
     * regardless of consent.
     * @param consentedClaims claims the client has consent for during this interactive flow session.
     */
    internal fun getUnfilteredReasonsToSendValidationCode(
        identifierClaims: List<CollectedClaim>,
        consentedClaims: List<CollectedClaim>
    ): List<ValidationCodeReason> {
        val allClaims = (identifierClaims + consentedClaims).distinctBy { it.claim.id }
        return validationCodeReasons.mapNotNull { reason ->
            getClaimValidatedBy(reason)?.let { claim ->
                val collectedClaim = allClaims.firstOrNull { it.claim.id == claim.id }
                if (collectedClaim?.verified != true) reason else null
            }
        }
    }

    /**
     * Send a [ValidationCode] to the [user] using the provided [media] if necessary.
     *
     * This method will:
     * - If there is no claim that requires a validation, then this method returns null.
     * - If a [ValidationCode] has been previously sent, then this method does not send a new code
     * and return the latest generated [ValidationCode] (even if it is expired).
     * - Otherwise, send a validation code to the [user] using the provided [media] to validate claims collected by this
     * authorization server.
     */
    @Transactional
    open suspend fun getOrSendValidationCode(
        session: OnGoingInteractiveFlowSession,
        user: User,
        media: ValidationCodeMedia
    ): ValidationCode? {
        val consentedScopes = oauth2Manager.fetchOAuth2(session).consentedScopes ?: emptyList()
        val identifierClaims = collectedClaimManager.findIdentifierByUserId(user.id)
        val consentedClaims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
            userId = user.id,
            consentedScopes = consentedScopes
        )

        val reasons = getReasonsToSendValidationCode(
            identifierClaims = identifierClaims,
            consentedClaims = consentedClaims
        ).filter { it.media == media }
        if (reasons.isEmpty()) return null

        val allClaims = (identifierClaims + consentedClaims).distinctBy { it.claim.id }
        val existingCode = validationCodeManager.findLatestCodeSentByMediaDuringSession(
            session = session,
            media = media,
            includesExpired = true
        )
        return if (existingCode == null) {
            validationCodeManager.queueRequiredValidationCodes(
                user = user,
                session = session,
                reasons = reasons,
                collectedClaims = allClaims
            ).firstOrNull()
        } else existingCode
    }

    /**
     * Resend the codes that were previously sent to this [user] during the [session].
     * Return the list of codes that have been resent.
     *
     * For a code to be resent using a given media, all previous code sent using this media must have passed
     * their [ValidationCode.resendDate]. A null [ValidationCode.resendDate] correspond to a never expiring code.
     */
    @Transactional
    open suspend fun resendValidationCode(
        session: OnGoingInteractiveFlowSession,
        user: User,
        media: ValidationCodeMedia
    ): ResendResult {
        val existingCode = validationCodeManager.findLatestCodeSentByMediaDuringSession(
            session = session,
            media = media,
            includesExpired = true,
        )
        if (existingCode == null || !validationCodeManager.canBeRefreshed(existingCode)) {
            return ResendResult(
                resent = false,
                validationCode = existingCode,
            )
        }

        val collectedClaims = collectedClaimManager.findByUserId(userId = user.id)

        val result = validationCodeManager.refreshAndQueueValidationCode(
            user = user,
            session = session,
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
        session: OnGoingInteractiveFlowSession,
        media: ValidationCodeMedia,
        code: String
    ) {
        val validationCodes = findCodesSentDuringSession(
            session = session,
            media = media
        )

        val matchingValidationCode = validationCodes.firstOrNull { it.code == code }
        if (matchingValidationCode == null) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.claim_validation.invalid_code",
                descriptionId = "description.flow.claim_validation.invalid_code"
            )
        }
        if (matchingValidationCode.expired) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.claim_validation.expired_code",
                descriptionId = "description.flow.claim_validation.expired_code"
            )
        }

        val claims = matchingValidationCode.reasons.mapNotNull(this::getClaimValidatedBy)
        collectedClaimManager.validateClaims(
            userId = session.userId!!,
            claims = claims
        )
    }

    /**
     * Return the code we have sent to the user to validate a claim during the [session].
     * If a [media] is provided, it will only return codes send using this [media].
     *
     * This method ignores return codes that have been sent for other reason like resetting user password, etc.
     */
    internal suspend fun findCodesSentDuringSession(
        session: InteractiveFlowSession,
        media: ValidationCodeMedia? = null,
    ): List<ValidationCode> {
        val codes = validationCodeManager.findCodeForReasonsDuringSession(
            session = session,
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
