package com.sympauthy.business.model.user

data class UserWithClaims(
    val user: User,
    val collectedClaims: List<CollectedClaim>
)
