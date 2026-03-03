package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class WebAuthorizationFlowTotpEnrollmentManager(
    @Inject private val totpManager: TotpManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    data class EnrollmentData(
        val enrollment: TotpEnrollment,
        val uri: String,
        val secret: String
    )

    /**
     * Initiates a new TOTP enrollment for the [user] and returns the data needed to display the enrollment screen:
     * an otpauth:// URI for QR code generation and the raw base32 secret for manual entry.
     *
     * Any existing unconfirmed enrollment for the user is replaced.
     */
    suspend fun getEnrollmentData(user: User): EnrollmentData {
        val enrollment = totpManager.initiateEnrollment(user)
        val issuer = getIssuer()
        val account = getAccount(user)
        return EnrollmentData(
            enrollment = enrollment,
            uri = totpManager.buildOtpauthUri(issuer, account, enrollment.secret),
            secret = totpManager.encodeSecretToBase32(enrollment.secret)
        )
    }

    /**
     * Confirms the pending TOTP enrollment for [user] by validating the [code] from their authenticator app.
     *
     * Throws a recoverable [com.sympauthy.business.exception.BusinessException] if:
     * - no pending enrollment exists (the user has not initiated enrollment).
     * - the [code] is invalid.
     */
    suspend fun confirmEnrollment(user: User, code: String): TotpEnrollment {
        val pendingEnrollment = totpManager.findPendingEnrollmentOrNull(user.id)
            ?: throw recoverableBusinessExceptionOf(
                detailsId = "flow.mfa.totp.enroll.no_pending_enrollment",
                descriptionId = "description.flow.mfa.totp.enroll.no_pending_enrollment"
            )

        return totpManager.confirmEnrollment(pendingEnrollment, code)
            ?: throw recoverableBusinessExceptionOf(
                detailsId = "flow.mfa.totp.enroll.invalid_code",
                descriptionId = "description.flow.mfa.totp.enroll.invalid_code"
            )
    }

    /**
     * Derives the TOTP issuer label from the configured auth issuer URL (e.g. "auth.example.com").
     */
    private fun getIssuer(): String {
        val issuerUrl = uncheckedAuthConfig.orThrow().issuer
        return try {
            URI(issuerUrl).host ?: issuerUrl
        } catch (_: Exception) {
            issuerUrl
        }
    }

    /**
     * Returns the user's email address as the TOTP account label, falling back to the user ID.
     */
    private suspend fun getAccount(user: User): String {
        return collectedClaimManager.findByUserId(user.id)
            .firstOrNull { it.claim.id == OpenIdClaim.EMAIL.id }
            ?.value?.toString()
            ?: user.id.toString()
    }
}
