package com.sympauthy.business.model.jwt

data class DecodedJwt(
    val id: String?,
    val subject: String?,
    val keyId: String?
)
