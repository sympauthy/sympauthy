package com.sympauthy.business.model.code

import com.sympauthy.business.model.Expirable
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.code.ValidationCodeMedia.SMS
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import java.time.LocalDateTime
import java.util.*

/**
 * A validation code sent to the end-user identified by [userId].
 * The [reasons] why we are sending the validation code to the end-user determine the [media] we are using.
 */
class ValidationCode(
    /**
     * Uniq identifier of the code.
     */
    val id: UUID,
    /**
     * The validation code the end-user will have to enter.
     *
     * It is stored as a string to accommodate futur validation that may not be integer-based
     * (ex: MFA with authenticator).
     */
    val code: String,

    /**
     * The identifier of the end-user we are sending the validation code to.
     */
    val userId: UUID,

    /**
     * The media used to send the validation code to the end-user. (ex. email)
     */
    val media: ValidationCodeMedia,

    /**
     * List of reason we sent the
     */
    val reasons: List<ValidationCodeReason>,

    /**
     * Identifier of the authorization attempt that requested a validation.
     */
    val attemptId: UUID?,

    val creationDate: LocalDateTime,

    /**
     * The date at which this validation code can be resent to the user on explicit demand.
     * If null, the validation code cannot be resent to the user.
     */
    val resendDate: LocalDateTime?,

    /**
     * The date when this authorization server received a matching code from the user.
     */
    val validationDate: LocalDateTime? = null,

    override val expirationDate: LocalDateTime
) : Expirable

/**
 * Enumeration of what we are trying to validation by using a validation code.
 */
enum class ValidationCodeReason(
    val media: ValidationCodeMedia
) {
    /**
     * A validation code has been sent by email to the end-user to verify its email claim.
     */
    EMAIL_CLAIM(EMAIL),

    /**
     * A validation code has been sent by SMS to the end-user to verify its phone number claim.
     */
    PHONE_NUMBER_CLAIM(SMS),
    RESET_PASSWORD(EMAIL),
}

/**
 * Enumeration of media used to send the validation code to the end-user.
 */
enum class ValidationCodeMedia(
    /**
     * The claim required to send the media to the end-user.
     * ex. The OpenID ```email``` claim is required to email the end-user.
     */
    val claim: String
) {
    EMAIL(OpenIdConnectClaimId.EMAIL),
    SMS(OpenIdConnectClaimId.PHONE_NUMBER)
}
