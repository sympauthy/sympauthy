package com.sympauthy.business.model.provider

import com.sympauthy.business.model.user.RawProviderClaims
import java.time.LocalDateTime
import java.util.*

data class ProviderUserInfo(
    /**
     * Identifier of the provider providing those user information.
     */
    val providerId: String,
    /**
     * Identifier of the user.
     */
    val userId: UUID,
    /**
     * Last time this application fetched the info from the provider.
     */
    val fetchDate: LocalDateTime,
    /**
     * Last time this application detected a change of the info returned by the provider.
     */
    val changeDate: LocalDateTime,
    val userInfo: RawProviderClaims
)
