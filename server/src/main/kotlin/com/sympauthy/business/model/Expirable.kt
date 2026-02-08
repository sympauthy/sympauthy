package com.sympauthy.business.model

import java.time.LocalDateTime

/**
 * An object with an expiration date. Once the expiration date is reached, the object is considered expired and
 * should not be allowed to perform any operation.
 */
interface Expirable {
    val expirationDate: LocalDateTime

    val expired: Boolean
        get() = expirationDate.isBefore(LocalDateTime.now())
}

/**
 * An object that may expire.
 */
interface MaybeExpirable {
    val expirationDate: LocalDateTime?

    val expired: Boolean
        get() = expirationDate?.isBefore(LocalDateTime.now()) == true
}
