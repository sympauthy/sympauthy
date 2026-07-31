package com.sympauthy.business.model.flow

/**
 * The machine-readable action an [InteractiveFlowPurpose.CONFIRM] gate asks the end-user to approve.
 *
 * Exposed to the custom confirmation UI (via the confirm flow controller) so it can render the localized copy
 * describing what the end-user is about to approve. Stored on the session's attached confirm record
 * ([InteractiveFlowSessionConfirm]).
 */
enum class ConfirmActionType {
    /**
     * The end-user is asked to approve enrolling a multi-factor authentication method, initiated on their
     * behalf through a standalone MFA-enrollment endpoint by a client or by an administrator.
     */
    ENROLL_MFA
}
