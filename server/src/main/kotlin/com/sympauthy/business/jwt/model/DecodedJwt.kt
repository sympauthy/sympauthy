package com.sympauthy.business.jwt.model

data class DecodedJwt(
    val id: String?,
    val subject: String?,
    val keyId: String?
)
