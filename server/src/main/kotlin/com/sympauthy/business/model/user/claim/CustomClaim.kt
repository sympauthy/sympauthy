package com.sympauthy.business.model.user.claim

class CustomClaim(
    id: String,
    dataType: ClaimDataType,
    required: Boolean,
    allowedValues: List<Any>?
): Claim(
    id = id,
    enabled = true,
    verifiedId = null, // Add support for verification on custom claim.
    dataType = dataType,
    group = null,
    required = required,
    userInputted = false,
    allowedValues = allowedValues
) {
    override val readScopes = emptySet<String>()

    override val writeScopes = emptySet<String>()
}
