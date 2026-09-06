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
     * Time at which the provider was linked to the user.
     *
     * Written when the link is created and never moved afterwards, so it stays the age of the link rather than
     * the recency of the last sign-in with this provider.
     */
    val linkDate: LocalDateTime,
    /**
     * Last time this application fetched the info from the provider.
     */
    val fetchDate: LocalDateTime,
    /**
     * Last time this application detected a change of the info returned by the provider.
     */
    val changeDate: LocalDateTime,
    /**
     * Identifier of the interactive flow session this link is still provisional for, or null once it is
     * permanent. See [com.sympauthy.data.model.SessionScoped].
     */
    val sessionId: UUID?,
    val userInfo: RawProviderClaims
)
