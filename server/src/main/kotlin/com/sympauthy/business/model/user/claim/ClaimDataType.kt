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
    val typeClass: KClass<*>,
    /**
     * Whether a claim of this type may be named in `auth.identifier-claims`.
     *
     * A type qualifies when a value of it is something one person is known by and types to sign in. A
     * [NUMBER] is one: an employee number identifies a person. The rest name a property somebody shares
     * with everybody else who has it — a [BOOLEAN] identifier would cap a deployment at two accounts, a
     * [DATE] one at a single account per day, and a [TIMEZONE] one at a single account per zone, which is
     * not an identity but a collision waiting for the second person. See `docs/identifier-claims.md`.
     */
    val canIdentify: Boolean = false
) {
    BOOLEAN(Boolean::class),
    DATE(String::class),
    EMAIL(String::class, canIdentify = true),
    NUMBER(Long::class, canIdentify = true),
    PHONE_NUMBER(String::class, canIdentify = true),
    STRING(String::class, canIdentify = true),
    TIMEZONE(String::class)
}
