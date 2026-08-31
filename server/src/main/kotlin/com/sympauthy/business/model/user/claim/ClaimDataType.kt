package com.sympauthy.business.model.user.claim

import kotlin.reflect.KClass

/**
 * Enumeration of supported data type for a user claim.
 */
enum class ClaimDataType(
    /**
     * Primitive type used to exchange the claim between the authorization server and its clients.
     *
     * ex: Email are encoded as String.
     */
    val typeClass: KClass<*>
) {
    BOOLEAN(String::class),
    DATE(String::class),
    EMAIL(String::class),
    NUMBER(Long::class),
    PHONE_NUMBER(String::class),
    STRING(String::class),
    TIMEZONE(String::class)
}

/**
 * The name this type is published under, and the one a failure naming a type interpolates.
 */
val ClaimDataType.wireName: String
    get() = name.lowercase()
