package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.EnabledFeaturesConfig

/**
 * Interface shared by all components sending validation code to the user using a given media.
 */
interface ValidationCodeMediaSender {

    /**
     * The [media] used by this component to send a validation code to the user.
     */
    val media: ValidationCodeMedia

    /**
     * Return true if sending validation code to user through the media is enabled on this authorization server.
     *
     * In order for the authorization server to send a validation code:
     * - the media must be enabled in [EnabledFeaturesConfig].
     * - the media must be configured (ex. having an SMTP config for email).
     */
    val enabled: Boolean

    /**
     * Send the [validationCode] to the [user].
     *
     * Depending on the implementation, this method may queue the sending and return immediately. A message queued
     * is written in the transaction the caller runs in, and leaves only once it has committed.
     */
    suspend fun sendValidationCode(
        user: User,
        collectedClaim: CollectedClaim,
        validationCode: ValidationCode
    )
}
