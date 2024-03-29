package com.sympauthy.business.model.user.claim

class CustomClaim(
    id: String,
    dataType: ClaimDataType,
    required: Boolean
): Claim(
    id = id,
    dataType = dataType,
    group = null,
    required = required
) {
    override val readScopes = emptySet<String>()

    override val writeScopes = emptySet<String>()
}
