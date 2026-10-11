package com.sympauthy.business.model.user

import com.sympauthy.business.model.user.claim.EnabledClaim
import java.util.*

/**
 * An update for a claim collected from the user by this authorization server as a first-party.
 */
data class CollectedClaimUpdate(
    /**
     * Claim to update.
     */
    val claim: EnabledClaim,
    /**
     * New value for the [claim].
     *
     * Depending on the [value], this authorization server will do the following action.
     * - [Optional.of]: the value collected will be replaced by the new value.
     * - [Optional.empty]: the value collected will be replaced by null.
     * - null: the previously collected value will be deleted. The claim will not be considered collected anymore.
     */
    val value: Optional<Any>?
)
