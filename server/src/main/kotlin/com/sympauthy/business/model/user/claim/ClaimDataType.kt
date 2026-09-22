package com.sympauthy.business.model.user.claim

import kotlin.reflect.KClass

/**
 * Enumeration of supported data type for a user claim.
 *
 * The type a deployment declares here is what decides the form a value of that claim takes
 * everywhere it is published, rather than the type the value happens to be carrying when it gets
 * there: `docs/design-faq.md`.
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
