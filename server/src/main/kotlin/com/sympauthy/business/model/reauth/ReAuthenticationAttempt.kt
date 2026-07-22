package com.sympauthy.business.model.reauth

import com.sympauthy.business.model.Expirable
import java.time.LocalDateTime
import java.util.*

/**
 * Reason a re-authentication (forced re-login / step-up) was requested.
 *
 * The re-authentication component is generic: it only proves that the end-user currently controls a specific
 * account. Each consumer feature declares its own [ReAuthenticationPurpose] and performs the privileged action
 * once the proof is obtained.
 */
enum class ReAuthenticationPurpose {
    /**
     * Prove ownership of an existing account before attaching a new provider identity to it.
     */
    PROVIDER_ATTACH
}

/**
 * Primary credential with which the end-user proved ownership of the target account.
 *
 * This is limited to the primary sign-in factors. Any second factor (e.g. TOTP) is enforced afterward by the
 * normal MFA step of the authorization flow, not as a standalone re-authentication method.
 */
enum class ReAuthenticationMethod {
    PASSWORD,
    PROVIDER
}

/**
 * A single-purpose challenge proving that the end-user currently controls the account identified by
 * [targetUserId]. Decoupled from the OAuth2 authorization attempt so it can be reused by any feature that needs
 * step-up authentication.
 */
sealed class ReAuthenticationAttempt(
    /**
     * Unique identifier of this re-authentication attempt.
     */
    val id: UUID,
    /**
     * The user whose ownership must be proven before the [purpose] action is performed.
     */
    val targetUserId: UUID,
    /**
     * Why the re-authentication was requested.
     */
    val purpose: ReAuthenticationPurpose,
    override val expirationDate: LocalDateTime
) : Expirable

/**
 * A re-authentication attempt that is still waiting for the end-user to prove ownership of [targetUserId].
 */
class PendingReAuthenticationAttempt(
    id: UUID,
    targetUserId: UUID,
    purpose: ReAuthenticationPurpose,
    expirationDate: LocalDateTime,
    /**
     * When the re-authentication was requested.
     */
    val attemptDate: LocalDateTime
) : ReAuthenticationAttempt(id, targetUserId, purpose, expirationDate)

/**
 * A re-authentication attempt whose ownership proof has been provided.
 */
class PassedReAuthenticationAttempt(
    id: UUID,
    targetUserId: UUID,
    purpose: ReAuthenticationPurpose,
    expirationDate: LocalDateTime,
    /**
     * When the re-authentication was requested.
     */
    val attemptDate: LocalDateTime,
    /**
     * When the end-user proved ownership of [targetUserId].
     */
    val passedDate: LocalDateTime,
    /**
     * The primary credential the end-user used to prove ownership.
     */
    val passedMethod: ReAuthenticationMethod
) : ReAuthenticationAttempt(id, targetUserId, purpose, expirationDate)
