package com.sympauthy.business.model.user.claim

import kotlin.reflect.KClass

/**
 * Enumeration of supported data type for a user claim.
 *
 * The type a deployment declares here decides the form a value of that claim takes everywhere it is
 * published. The type the value happens to be carrying decides nothing: it is an artifact of how the
 * value round-tripped through the object mapper, and a publisher reading the wire form off it
 * publishes whatever that round trip produced.
 */
enum class ClaimDataType(
    /**
     * Primitive type used to exchange the claim between the authorization server and its clients.
     *
     * It is the type a validated value is held in, the type a stored one is read back as, and the
     * JSON type a published one takes.
     *
     * ex: Email are encoded as String.
     */
    val typeClass: KClass<*>
) {
    BOOLEAN(Boolean::class),
    DATE(String::class),
    EMAIL(String::class),
    NUMBER(Long::class),
    PHONE_NUMBER(String::class),
    STRING(String::class),
    TIMEZONE(String::class)
}
