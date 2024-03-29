package com.sympauthy.business.model.user

import java.time.LocalDateTime
import java.util.*

data class User(
    val id: UUID,
    val status: UserStatus,
    val creationDate: LocalDateTime
)
