package com.sympauthy.business.flow.model

/**
 * Who initiated the action an [InteractiveFlowPurpose.CONFIRM] gate asks the end-user to approve.
 */
enum class ConfirmInitiatedBy {
    /**
     * A client, named by [InteractiveFlowSessionConfirm.clientId].
     */
    CLIENT,

    /**
     * An administrator, through the admin API.
     */
    ADMIN
}
