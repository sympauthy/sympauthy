package com.sympauthy.business.model.oauth2

import com.sympauthy.business.model.MaybeExpirable
import java.time.LocalDateTime
import java.util.*

data class EncodedAuthenticationToken(
    val id: UUID,
    val type: AuthenticationTokenType,
    val token: String,
    val scopes: List<String>,
    val issueDate: LocalDateTime,
    override val expirationDate: LocalDateTime?
) : MaybeExpirable
