package com.sympauthy.business.model.grant

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf

/**
 * Whether a grant was opened for a client, and for the audience that client belongs to now.
 *
 * A refusal is a value rather than a throw, because what it becomes is the caller's to decide: the token
 * endpoint answers it as a protocol error, a resource as a challenge, and introspection as an inactive token.
 * Each refusal is [Refused.toBusinessException], which every caller renames from.
 */
sealed class GrantOpenedForResult {

    /**
     * The grant was opened for the client, and for the audience it belongs to now.
     */
    data object Opened : GrantOpenedForResult()

    /**
     * The grant may not be used by the client.
     */
    sealed class Refused : GrantOpenedForResult() {

        /**
         * This refusal as the failure a caller throws, under its own code and values.
         */
        abstract fun toBusinessException(): BusinessException
    }

    /**
     * The grant was opened for the client identified by [grantClientId], which is not the one asking.
     */
    data class OpenedForAnotherClient(
        val grantClientId: String,
        val clientId: String
    ) : Refused() {

        override fun toBusinessException() = businessExceptionOf(
            "token.mismatching_client",
            "description.token.mismatching_client"
        )
    }

    /**
     * The grant was opened for the audience identified by [grantAudienceId], and the client identified by
     * [clientId] has since moved to the one identified by [clientAudienceId].
     */
    data class OpenedForAnotherAudience(
        val grantAudienceId: String,
        val clientId: String,
        val clientAudienceId: String
    ) : Refused() {

        override fun toBusinessException() = businessExceptionOf(
            "grant.mismatching_audience",
            "description.grant.mismatching_audience",
            "grantAudienceId" to grantAudienceId,
            "clientId" to clientId,
            "clientAudienceId" to clientAudienceId
        )
    }
}
