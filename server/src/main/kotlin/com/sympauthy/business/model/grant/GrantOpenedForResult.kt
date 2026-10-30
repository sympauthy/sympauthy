package com.sympauthy.business.model.grant

import com.sympauthy.business.exception.BusinessException
import io.micronaut.http.HttpStatus.BAD_REQUEST

/**
 * Whether a grant was opened for a client, and for the audience that client belongs to now.
 *
 * A refusal is a value rather than a throw, because what it becomes is the caller's to decide: the token
 * endpoint answers it as a protocol error, a resource as a challenge, and introspection as an inactive token.
 */
sealed class GrantOpenedForResult {

    /**
     * The grant was opened for the client, and for the audience it belongs to now.
     */
    data object Opened : GrantOpenedForResult()

    /**
     * The grant may not be used by the client, under the code, the description and the values every caller
     * answers the refusal with.
     */
    sealed class Refused : GrantOpenedForResult() {
        abstract val detailsId: String
        abstract val descriptionId: String
        abstract val values: Map<String, String>

        /**
         * This refusal as the failure a caller outside the protocol endpoints throws.
         */
        fun toBusinessException() = BusinessException(
            recoverable = false,
            detailsId = detailsId,
            descriptionId = descriptionId,
            values = values,
            recommendedStatus = BAD_REQUEST
        )
    }

    /**
     * The grant was opened for the client identified by [grantClientId], which is not the one identified by
     * [clientId] asking.
     */
    data class OpenedForAnotherClient(
        val grantClientId: String,
        val clientId: String
    ) : Refused() {
        override val detailsId = "token.mismatching_client"
        override val descriptionId = "description.token.mismatching_client"
        override val values = mapOf("grantClientId" to grantClientId, "clientId" to clientId)
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
        override val detailsId = "grant.mismatching_audience"
        override val descriptionId = "description.grant.mismatching_audience"
        override val values = mapOf(
            "grantAudienceId" to grantAudienceId,
            "clientId" to clientId,
            "clientAudienceId" to clientAudienceId
        )
    }
}
