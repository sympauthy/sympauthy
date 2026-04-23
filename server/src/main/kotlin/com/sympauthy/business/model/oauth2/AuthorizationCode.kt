package com.sympauthy.business.model.oauth2

import com.sympauthy.business.model.Expirable
import java.time.LocalDateTime
import java.util.*

data class AuthorizationCode(
    val attemptId: UUID,
    val code: String,
    val creationDate: LocalDateTime,
    override val expirationDate: LocalDateTime
) : Expirable
